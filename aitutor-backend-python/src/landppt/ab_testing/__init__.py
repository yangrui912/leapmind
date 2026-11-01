"""
A/B testing framework for M5 AI备课 Prompt optimization.

Layers:
  ABExperiment    — experiment definition, assignment, result tracking
  EvaluationReport — statistical evaluation and winner selection
"""
from .ab_test_framework import ABExperiment, SampleRecord, EvaluationReport, MetricDefinition

__all__ = [
    "ABExperiment",
    "SampleRecord",
    "EvaluationReport",
    "MetricDefinition",
]
