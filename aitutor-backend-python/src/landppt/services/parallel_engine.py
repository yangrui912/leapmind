"""
Parallel execution engine for concurrent AI calls.

Replaces serial per-page loops in Stage 2/3 with limited concurrency:
  - asyncio.Semaphore caps max in-flight calls (default: 5)
  - progress_callback yields results as they complete (for SSE push)
  - return_exceptions=True prevents one failure from killing others
  - Items that error resolve to None (caller filters with .filter(Boolean))
"""
import asyncio
import logging
import time
from typing import Any, Awaitable, Callable, Optional

logger = logging.getLogger(__name__)


class ParallelExecutor:
    """Limit-concurrency executor for async tasks.

    Usage:
        executor = ParallelExecutor(max_concurrency=5)
        results = await executor.map(
            items=[1, 2, 3],
            fn=lambda x: some_async_call(x),
            progress_callback=lambda idx, result: print(f"done: {idx}"),
        )
        # results[i] is None if task i raised
        valid = [r for r in results if r is not None]
    """

    def __init__(self, max_concurrency: int = 5):
        if max_concurrency < 1:
            raise ValueError("max_concurrency must be >= 1")
        self.max_concurrency = max_concurrency
        self.semaphore = asyncio.Semaphore(max_concurrency)
        self._total_tasks = 0
        self._completed_tasks = 0
        self._start_time = 0.0

    async def map(
        self,
        items: list,
        fn: Callable[[Any], Awaitable[Any]],
        *,
        progress_callback: Optional[Callable[[int, Any], Awaitable[None]]] = None,
        timeout_seconds: Optional[float] = None,
    ) -> list:
        """Apply fn concurrently to each item, with concurrency limiting.

        Args:
            items: List of inputs (one per task).
            fn: Async function (item) -> result. Called with one item at a time.
            progress_callback: Async callback (index, result) invoked as each
                               task completes. Called with None result on error.
            timeout_seconds: Optional per-task timeout.

        Returns:
            List of results, same order as items. None for tasks that raised.
        """
        self._total_tasks = len(items)
        self._completed_tasks = 0
        self._start_time = time.monotonic()

        sem = self.semaphore

        async def _run_with_semaphore(index: int, item: Any) -> tuple[int, Any]:
            async with sem:
                try:
                    if timeout_seconds is not None:
                        result = await asyncio.wait_for(
                            fn(item), timeout=timeout_seconds
                        )
                    else:
                        result = await fn(item)
                    return index, result
                except asyncio.TimeoutError:
                    logger.warning(
                        f"Task {index} timed out after {timeout_seconds}s"
                    )
                    return index, None
                except Exception as e:
                    logger.warning(
                        f"Task {index} failed: {e}", exc_info=True
                    )
                    return index, None

        # Launch all tasks (semaphore limits actual concurrency)
        tasks = [
            _run_with_semaphore(i, item) for i, item in enumerate(items)
        ]

        # Gather with return_exceptions so partial failures don't abort all
        raw_results = await asyncio.gather(*tasks, return_exceptions=True)

        # Reconstruct ordered results list; invoke progress callback
        results: list = [None] * len(items)
        for outcome in raw_results:
            if isinstance(outcome, tuple) and len(outcome) == 2:
                index, value = outcome
                results[index] = value
                self._completed_tasks += 1
                if progress_callback:
                    try:
                        await progress_callback(index, value)
                    except Exception as cb_err:
                        logger.warning(f"Progress callback error: {cb_err}")
            elif isinstance(outcome, Exception):
                logger.error(f"Unexpected exception in gather: {outcome}")

        return results

    @property
    def progress(self) -> float:
        """Completion ratio 0.0 ~ 1.0."""
        if self._total_tasks == 0:
            return 1.0
        return self._completed_tasks / self._total_tasks

    @property
    def elapsed_seconds(self) -> float:
        """Seconds since map() was called."""
        if self._start_time == 0:
            return 0.0
        return time.monotonic() - self._start_time

    def estimate_speedup(self, total_items: int, avg_time_per_item: float) -> float:
        """Estimate speedup ratio compared to serial execution.

        Args:
            total_items: Number of tasks.
            avg_time_per_item: Average time per task in seconds.

        Returns:
            Speedup multiplier (serial_time / parallel_time).
        """
        if total_items <= 0 or avg_time_per_item <= 0:
            return 1.0
        serial = total_items * avg_time_per_item
        batches = (total_items + self.max_concurrency - 1) // self.max_concurrency
        parallel = batches * avg_time_per_item
        return max(1.0, serial / parallel)
