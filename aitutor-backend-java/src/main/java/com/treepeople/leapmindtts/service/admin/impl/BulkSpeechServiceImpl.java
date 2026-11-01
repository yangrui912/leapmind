package com.treepeople.leapmindtts.service.admin.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.mapper.AudioSegmentMapper;
import com.treepeople.leapmindtts.mapper.TeachingContentMapper;
import com.treepeople.leapmindtts.pojo.dto.*;
import com.treepeople.leapmindtts.pojo.entity.AudioSegment;
import com.treepeople.leapmindtts.pojo.entity.LessonSession;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.service.admin.BulkSpeechCache;
import com.treepeople.leapmindtts.service.admin.BulkSpeechService;
import com.treepeople.leapmindtts.service.admin.LessonSessionService;
import com.treepeople.leapmindtts.service.impl.TtsBatchServiceImpl;
import com.treepeople.leapmindtts.service.lesson.*;
import com.treepeople.leapmindtts.util.SegmentIndexingStrategy;
import com.treepeople.leapmindtts.util.WavMergeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批量语音合成服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkSpeechServiceImpl implements BulkSpeechService {

    /**
     * Slide处理结果内部类
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class SlideProcessResult {
        private int segmentCount;
        private String polishedText;
    }

    /**
     * Slide文本处理结果内部类（用于预处理）
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class SlideTextProcessResult {
        private int segmentCount;
        private String polishedText;
    }

    private final AudioSegmentMapper audioSegmentMapper;
    private final SegmentedSpeechService segmentedSpeechService;
    private final VoiceDatabaseService voiceDatabaseService;
    private final PPTContextualPolishing pptContextualPolishing;
    private final TextToSpeechService textToSpeechService;
    private final PageLevelAudioService pageLevelAudioService;
    private final AIModelService aiModelService;
    private final LessonSessionService lessonSessionService;
    private final BulkSpeechCache bulkSpeechCache;
    private final NarrationBridgeService narrationBridgeService;
    private final TtsBatchServiceImpl ttsBatchService;
    private final TeachingContentMapper teachingContentMapper;
    private final ObjectMapper objectMapper;

    @Override
    public BulkSynthesisResponse processBulkSynthesis(BulkSynthesisRequest request, Long userId) {
        String courseId = (request.getCourseId() != null && !request.getCourseId().isBlank())
                ? request.getCourseId() : generateCourseId();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("开始批量语音合成，会话ID: {}, PPT标题: {}, slides数量: {}", courseId, request.getTitle(), request.getSlides().size());

        if (voiceDatabaseService == null) {
            log.error("voiceDatabaseService为null，无法继续处理");
            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("FAILED")
                    .message("服务依赖注入失败：voiceDatabaseService为null")
                    .startTime(startTime)
                    .build();
        }

        try {
            // 1. 按页码排序slides，处理null值
            List<BulkSynthesisRequest.SlideData> sortedSlides = request.getSlides().stream()
                    .filter(slide -> slide != null && slide.getPageNumber() != null)
                    .sorted(Comparator.comparing(BulkSynthesisRequest.SlideData::getPageNumber))
                    .collect(Collectors.toList());

            log.info("排序后的slides数量: {}", sortedSlides.size());

            if (sortedSlides.size() != request.getSlides().size()) {
                log.warn("过滤掉了 {} 个无效的slide（pageNumber为null）",
                        request.getSlides().size() - sortedSlides.size());
            }

            // 2. 创建会话
            String originalText = buildOriginalText(sortedSlides);
            boolean sessionCreated = voiceDatabaseService.createCompleteSession(
                    courseId, request.getTitle(), originalText, null, new ArrayList<>(), userId);

            if (!sessionCreated) {
                log.error("创建会话失败，会话ID: {}", courseId);
                return BulkSynthesisResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("创建会话失败")
                        .startTime(startTime)
                        .build();
            }

            // 为了向后兼容，原有接口直接设置为SYNTHESIZED状态（跳过审核流程）
            updateSessionStatus(courseId, "SYNTHESIZED", "SYSTEM", LocalDateTime.now(), "直接合成，跳过审核");

            // 3. 处理所有slides并收集润色文本
            int totalContentPoints = calculateTotalContentPoints(sortedSlides);
            int processedSegments = 0;
            StringBuilder polishedTextBuilder = new StringBuilder();

            for (BulkSynthesisRequest.SlideData slide : sortedSlides) {
                try {
                    SlideProcessResult result = processSlideWithPolishedText(courseId, slide, request.getOptions());
                    processedSegments += result.getSegmentCount();

                    // 收集润色后的文本
                    if (result.getPolishedText() != null && !result.getPolishedText().trim().isEmpty()) {
                        polishedTextBuilder.append("第").append(slide.getPageNumber()).append("页: ")
                                .append(slide.getTitle()).append("\n");
                        polishedTextBuilder.append(result.getPolishedText()).append("\n\n");
                    }

                    log.info("处理slide完成，页码: {}, 生成片段数: {}", slide.getPageNumber(), result.getSegmentCount());
                } catch (Exception e) {
                    log.error("处理slide失败，页码: {}", slide.getPageNumber(), e);
                    // 继续处理其他slides
                }
            }

            // 4. 更新会话的润色文本
            if (polishedTextBuilder.length() > 0) {
                try {
                    boolean updated = voiceDatabaseService.updateSessionPolishedText(courseId, polishedTextBuilder.toString());
                    if (updated) {
                        log.info("更新会话润色文本成功，会话ID: {}, 文本长度: {}", courseId, polishedTextBuilder.length());
                    } else {
                        log.warn("更新会话润色文本失败，会话ID: {}", courseId);
                    }
                } catch (Exception e) {
                    log.error("更新会话润色文本异常，会话ID: {}", courseId, e);
                }
            }

            log.info("批量语音合成完成，会话ID: {}, 总片段数: {}", courseId, processedSegments);

            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("COMPLETED")
                    .totalSlides(sortedSlides.size())
                    .totalContentPoints(totalContentPoints)
                    .message("批量合成完成，生成 " + processedSegments + " 个音频片段")
                    .startTime(startTime)
                    .build();

        } catch (Exception e) {
            log.error("批量语音合成失败，会话ID: {}", courseId, e);
            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("FAILED")
                    .message("处理失败: " + e.getMessage())
                    .startTime(startTime)
                    .build();
        }
    }

    @Override
    public BulkSynthesisResponse processBulkSynthesis(BulkSynthesisRequest request, String userJwt) {
        LocalDateTime startTime = LocalDateTime.now();
        String courseId = Optional.ofNullable(request.getCourseId())
                .filter(s -> !s.isBlank()).orElse(generateCourseId());
        long prepIdN = courseIdToPrepId(courseId);
        log.info("[新体系] 批量语音合成，courseId={} prepId={} title={} slides={} userJwt 存在={}",
                courseId, prepIdN, request.getTitle(),
                request.getSlides() != null ? request.getSlides().size() : 0,
                userJwt != null && !userJwt.isBlank());

        try {
            List<BulkSynthesisRequest.SlideData> sorted = request.getSlides() == null ? List.of()
                    : request.getSlides().stream()
                    .filter(s -> s != null && s.getPageNumber() != null)
                    .sorted(Comparator.comparing(BulkSynthesisRequest.SlideData::getPageNumber))
                    .collect(Collectors.toList());
            if (sorted.isEmpty()) {
                return BulkSynthesisResponse.builder()
                        .courseId(courseId).status("FAILED").message("slides 为空")
                        .startTime(startTime).build();
            }
            int totalContentPoints = sorted.stream()
                    .mapToInt(s -> s.getContentPoints() != null ? s.getContentPoints().size() : 0).sum();

            // 1) 老请求 → 新结构，首写 DB
            PptStructureDTO structure = convertBulkRequestToStructure(request, sorted);
            saveOrUpdateTeachingContent(prepIdN, request.getTitle(),
                    objectMapper.writeValueAsString(structure), courseId);

            // 2) 公共一键 TTS：收集 narration → 3 并发 M8 → [AUDIO_URL:] 回填 → 回写 DB
            int[] success = {0};
            success[0] = ttsBatchService.generateAndBackfill(structure, prepIdN, userJwt, p -> {
                log.debug("[新体系] TTS 进度 {}/{} slide={} status={}",
                        p.getCurrentIndex(), p.getTotalCount(), p.getCurrentTitle(), p.getStatus());
            });

            // 3) lesson_session 状态兼容现有管理后台展示
            try {
                if (voiceDatabaseService.getCompleteSessionInfo(courseId) == null) {
                    voiceDatabaseService.createCompleteSession(
                            courseId, request.getTitle(), originalTextFromStructure(structure), null, new ArrayList<>());
                }
                updateSessionStatus(courseId, "SYNTHESIZED", "SYSTEM",
                        LocalDateTime.now(), "新体系合成，跳过审核");
            } catch (Exception e) {
                log.warn("创建 lesson_session 展示记录失败(不影响主链路)，courseId={} err={}",
                        courseId, e.getMessage());
            }

            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("COMPLETED")
                    .totalSlides(sorted.size())
                    .totalContentPoints(totalContentPoints)
                    .message(String.format("新体系合成完成 %d 页，成功生成 %d 条旁白音频", sorted.size(), success[0]))
                    .startTime(startTime)
                    .build();

        } catch (Exception e) {
            log.error("[新体系] 批量合成失败 courseId={}", courseId, e);
            return BulkSynthesisResponse.builder()
                    .courseId(courseId).status("FAILED")
                    .message("处理失败: " + e.getMessage())
                    .startTime(startTime).build();
        }
    }

    /**
     * 处理单个slide并返回润色文本（新方法）
     */
    private SlideProcessResult processSlideWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                          BulkSynthesisRequest.BulkSynthesisOptions options) {

        log.info("开始处理页面，会话ID: {}, 页码: {}, 内容点数: {}",
                courseId, slide.getPageNumber(), slide.getContentPoints().size());

        // 检查是否为目录页面，如果是，使用特殊处理逻辑
        if (isAgendaOrTitlePage(slide)) {
            return processAgendaSlideWithPolishedText(courseId, slide, options);
        }

        // 普通页面的处理逻辑
        return processNormalSlideWithPolishedText(courseId, slide, options);
    }


    /**
     * 处理目录页面并返回润色文本
     */
    private SlideProcessResult processAgendaSlideWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                               BulkSynthesisRequest.BulkSynthesisOptions options) {

        log.info("处理目录页面，会话ID: {}, 页码: {}, 内容点数: {}",
                courseId, slide.getPageNumber(), slide.getContentPoints().size());

        List<PPTAudioSegment> pageAudioSegments = new ArrayList<>();
        String combinedContent = String.join(" ", slide.getContentPoints());
        String finalPolishedText = combinedContent;

        try {
            // 1. 文本润色（如果启用）
            if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                try {
                    String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, combinedContent, 0);
                    Integer hardCap = 200;

                    log.info("开始目录页面润色，页码: {}, 原文{}字, 上限{}字",
                            slide.getPageNumber(), combinedContent.length(), hardCap);

                    String polishedText = aiModelService.polishTextWithPrompt(combinedContent, customPrompt, hardCap).block(Duration.ofSeconds(25));
                    if (polishedText != null && !polishedText.trim().isEmpty()) {
                        finalPolishedText = polishedText;
                        log.info("目录页面润色完成，页码: {}, 原文{}字 -> 润色后{}字",
                                slide.getPageNumber(), combinedContent.length(), polishedText.length());
                    }
                } catch (Exception e) {
                    log.warn("目录页面润色失败，使用原文，页码: {}, 错误: {}",
                            slide.getPageNumber(), e.getMessage(), e);
                }
            }

            // 2. 分句和语音合成处理（保持原有逻辑）
            List<String> sentences = segmentedSpeechService.splitTextBySentence(finalPolishedText);

            for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                String sentence = sentences.get(sentenceIndex);

                try {
                    int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                            slide.getPageNumber(), 0, sentenceIndex);

                    PPTAudioSegment audioSegment = synthesizeAudioSegment(courseId, slide, 0,
                            globalSegmentIndex, combinedContent, finalPolishedText, sentence, options);

                    if (audioSegment != null && audioSegment.getAudioData() != null && audioSegment.getAudioData().length > 0) {
                        pageAudioSegments.add(audioSegment);
                    }
                } catch (Exception e) {
                    log.error("目录页面处理句子失败，句子: {}", sentenceIndex, e);
                }
            }

            // 3. 保存页面级音频数据
            if (!pageAudioSegments.isEmpty()) {
                try {
                    String audioFormat = options != null ? options.getAudioFormat() : "wav";
                    Integer sampleRate = options != null ? options.getSampleRate() : 16000;

                    pageLevelAudioService.savePageAudio(
                            courseId, slide.getPageNumber(), slide.getTitle(),
                            slide.getSlideType(), slide.getDescription(),
                            pageAudioSegments, audioFormat, sampleRate
                    );
                } catch (Exception e) {
                    log.error("保存目录页面音频时发生异常，页码: {}", slide.getPageNumber(), e);
                }
            }

            return SlideProcessResult.builder()
                    .segmentCount(pageAudioSegments.size())
                    .polishedText(finalPolishedText)
                    .build();

        } catch (Exception e) {
            log.error("处理目录页面失败，页码: {}", slide.getPageNumber(), e);
            return SlideProcessResult.builder()
                    .segmentCount(0)
                    .polishedText(combinedContent)
                    .build();
        }
    }

    /**
     * 处理普通页面并返回润色文本
     */
    private SlideProcessResult processNormalSlideWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                               BulkSynthesisRequest.BulkSynthesisOptions options) {

        List<PPTAudioSegment> pageAudioSegments = new ArrayList<>();
        StringBuilder slidePolishedText = new StringBuilder();
        int segmentCount = 0;

        for (int pointIndex = 0; pointIndex < slide.getContentPoints().size(); pointIndex++) {
            String contentPoint = slide.getContentPoints().get(pointIndex);

            if (contentPoint == null || contentPoint.trim().isEmpty()) {
                continue;
            }

            try {
                // 1. 文本润色
                String polishedText = contentPoint;
                if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                    try {
                        String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, contentPoint, pointIndex);
                        int originalLength = contentPoint.length();
                        Integer hardCap = isAgendaOrTitlePage(slide) ? 200 : originalLength + 200;

                        String result = aiModelService.polishTextWithPrompt(contentPoint, customPrompt, hardCap).block(Duration.ofSeconds(25));
                        if (result != null && !result.trim().isEmpty()) {
                            polishedText = result;
                            log.info("文本润色完成，页码: {}, 内容点: {}, 原文{}字 -> 润色后{}字",
                                    slide.getPageNumber(), pointIndex, originalLength, polishedText.length());
                        }
                    } catch (Exception e) {
                        log.warn("文本润色失败，使用原文，页码: {}, 内容点索引: {}",
                                slide.getPageNumber(), pointIndex, e);
                    }
                }

                // 收集润色后的文本
                if (slidePolishedText.length() > 0) {
                    slidePolishedText.append("\n");
                }
                slidePolishedText.append("- ").append(polishedText);

                // 2. 分句和语音合成处理（保持原有逻辑）
                List<String> sentences = segmentedSpeechService.splitTextBySentence(polishedText);

                for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                    String sentence = sentences.get(sentenceIndex);

                    try {
                        int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                                slide.getPageNumber(), pointIndex, sentenceIndex);

                        PPTAudioSegment audioSegment = synthesizeAudioSegment(courseId, slide, pointIndex,
                                globalSegmentIndex, contentPoint, polishedText, sentence, options);

                        if (audioSegment != null && audioSegment.getAudioData() != null && audioSegment.getAudioData().length > 0) {
                            pageAudioSegments.add(audioSegment);
                            segmentCount++;
                        }
                    } catch (Exception e) {
                        log.error("处理句子失败，页码: {}, 内容点: {}, 句子: {}",
                                slide.getPageNumber(), pointIndex, sentenceIndex, e);
                    }
                }

            } catch (Exception e) {
                log.error("处理内容点失败，页码: {}, 内容点索引: {}", slide.getPageNumber(), pointIndex, e);
            }
        }

        // 3. 保存页面级音频数据
        if (!pageAudioSegments.isEmpty()) {
            try {
                String audioFormat = options != null ? options.getAudioFormat() : "wav";
                Integer sampleRate = options != null ? options.getSampleRate() : 16000;

                pageLevelAudioService.savePageAudio(
                        courseId, slide.getPageNumber(), slide.getTitle(),
                        slide.getSlideType(), slide.getDescription(),
                        pageAudioSegments, audioFormat, sampleRate
                );
            } catch (Exception e) {
                log.error("保存页面音频时发生异常，页码: {}", slide.getPageNumber(), e);
            }
        }

        return SlideProcessResult.builder()
                .segmentCount(segmentCount)
                .polishedText(slidePolishedText.toString())
                .build();
    }

    /**
     * 语音合成（返回音频片段对象，不直接保存到数据库）
     */
    private PPTAudioSegment synthesizeAudioSegment(String courseId, BulkSynthesisRequest.SlideData slide,
                                                  int contentPointIndex, int globalSegmentIndex,
                                                  String originalText, String polishedText, String sentence,
                                                  BulkSynthesisRequest.BulkSynthesisOptions options) {
        try {
            // 1. 先查 Redis 缓存（L1 → L2），含穿透标记处理
            String textHash = BulkSpeechCache.textHash(sentence);
            byte[] audioData = bulkSpeechCache.getCachedAudio(textHash);

            if (audioData == null) {
                // ── 击穿防护：尝试获取互斥锁 ──
                boolean rebuildLocked = bulkSpeechCache.tryAcquireRebuildLock(textHash);
                if (rebuildLocked) {
                    try {
                        // 二次检查缓存（可能另一线程刚重建完）
                        audioData = bulkSpeechCache.getCachedAudio(textHash);
                        if (audioData == null) {
                            // 缓存未命中，调用阿里云 TTS 服务
                            try {
                                Mono<byte[]> synthesisResult = textToSpeechService.synthesizeSpeech(sentence);
                                audioData = synthesisResult.block(Duration.ofSeconds(30));
                                if (audioData == null || audioData.length == 0) {
                                    log.error("TTS合成结果为空，全局索引: {}", globalSegmentIndex);
                                    // 穿透防护：写入 null 标记，短 TTL
                                    bulkSpeechCache.putCachedAudioNull(textHash);
                                    return null;
                                }
                            } catch (Exception ttsError) {
                                log.error("TTS合成失败，全局索引: {}, 错误: {}",
                                        globalSegmentIndex, ttsError.getMessage(), ttsError);
                                // 穿透防护：写入 null 标记
                                bulkSpeechCache.putCachedAudioNull(textHash);
                                return null;
                            }
                            // 写入缓存（雪崩防护：TTL 自动抖动）
                            bulkSpeechCache.putCachedAudio(textHash, audioData);
                            log.debug("TTS 缓存写入，全局索引: {}", globalSegmentIndex);
                        }
                    } finally {
                        bulkSpeechCache.releaseRebuildLock(textHash);
                    }
                } else {
                    // 其他线程正在重建，自旋等待
                    audioData = bulkSpeechCache.spinWaitForRebuild(textHash);
                    if (audioData == null) {
                        log.warn("自旋等待重建失败，跳过该片段，全局索引: {}", globalSegmentIndex);
                        return null;
                    }
                    log.debug("自旋等待后命中缓存，全局索引: {}", globalSegmentIndex);
                }
            } else {
                log.debug("TTS 缓存命中，全局索引: {}", globalSegmentIndex);
            }

            long duration = estimateAudioDuration(audioData);

            String audioFormat = options != null ? options.getAudioFormat() : "wav";
            Integer sampleRate = options != null ? options.getSampleRate() : 16000;

            // 2. 构建PPTAudioSegment对象
            PPTAudioSegment audioSegment = PPTAudioSegment.builder()
                    .courseId(courseId)
                    .slidePageNumber(slide.getPageNumber())
                    .slideTitle(slide.getTitle())
                    .contentPointIndex(contentPointIndex)
                    .segmentIndex(globalSegmentIndex)
                    .slideType(slide.getSlideType())
                    .slideDescription(slide.getDescription())
                    .originalText(originalText)
                    .polishedText(polishedText)
                    .textContent(sentence)
                    .audioData(audioData)
                    .audioSize((long) audioData.length)
                    .duration(duration)
                    .audioFormat(audioFormat)
                    .sampleRate(sampleRate)
                    .checksum(generateChecksum(audioData))
                    .createdAt(LocalDateTime.now())
                    .build();

            log.debug("音频片段合成成功，全局索引: {}, 音频大小: {} bytes",
                    globalSegmentIndex, audioData.length);

            return audioSegment;

        } catch (Exception e) {
            log.error("语音合成失败，全局索引: {}", globalSegmentIndex, e);
            return null;
        }
    }

    /**
     * 生成音频数据校验和（SHA-256）
     */
    private String generateChecksum(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return "";
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(audioData);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            log.error("SHA-256 不可用，回退到长度校验", e);
            return "len_" + audioData.length;
        }
    }

    /**
     * 判断是否为目录或标题页
     */
    private boolean isAgendaOrTitlePage(BulkSynthesisRequest.SlideData slide) {
        // 检查slideType
        if (slide.getSlideType() != null) {
            String type = slide.getSlideType().toLowerCase();
            if ("agenda".equals(type) || "title".equals(type) || "thankyou".equals(type)) {
                return true;
            }
        }

        // 检查标题关键词
        if (slide.getTitle() != null) {
            String title = slide.getTitle().toLowerCase().trim();
            String[] agendaKeywords = {
                "目录", "大纲", "内容", "章节", "agenda", "contents", "outline",
                "课程安排", "学习内容", "主要内容", "课程大纲", "课程目录",
                "今日内容", "本次课程", "课程结构", "学习路径"
            };

            for (String keyword : agendaKeywords) {
                if (title.contains(keyword)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 生成会话ID
     */
    private String generateCourseId() {
        return "ppt_session_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构建原始文本
     */
    private String buildOriginalText(List<BulkSynthesisRequest.SlideData> slides) {
        StringBuilder sb = new StringBuilder();
        for (BulkSynthesisRequest.SlideData slide : slides) {
            sb.append("第").append(slide.getPageNumber()).append("页: ").append(slide.getTitle()).append("\n");
            for (String contentPoint : slide.getContentPoints()) {
                sb.append("- ").append(contentPoint).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 计算总内容点数量
     */
    private int calculateTotalContentPoints(List<BulkSynthesisRequest.SlideData> slides) {
        return slides.stream()
                .mapToInt(slide -> slide.getContentPoints().size())
                .sum();
    }

    /**
     * 估算音频时长（使用 WavMergeUtil 精确解析 WAV 文件头）
     */
    private long estimateAudioDuration(byte[] audioData) {
        return WavMergeUtil.estimateDurationMs(audioData);
    }

    // ================================================================
    //  新体系辅助方法
    // ================================================================

    /** courseId → prepId：数字型直接转；否则取 hashCode 绝对值作为稳定伪 ID */
    private long courseIdToPrepId(String courseId) {
        if (courseId == null || courseId.isBlank()) return 0L;
        try { return Long.parseLong(courseId.trim()); }
        catch (NumberFormatException e) {
            return Math.abs((long) courseId.hashCode()) | 0x10000000L; // 高位打标，避免与真实 ID 撞
        }
    }

    /**
     * 将老 BulkSynthesisRequest（slides:[{page_number,title,content_points[]}]）
     * 转成新体系 PptStructureDTO。
     *  notes = 标题 + 要点拼接（模拟 Python narration_text：整页一段话）
     *  bulletPoints = content_points
     *  imageSuggestion / highlightPoints = 尽量从 options 取，没有就留空
     */
    private PptStructureDTO convertBulkRequestToStructure(
            BulkSynthesisRequest req, List<BulkSynthesisRequest.SlideData> sorted) {

        PptStructureDTO structure = new PptStructureDTO();
        structure.setTitle(req.getTitle() != null ? req.getTitle() : "未命名PPT");

        List<PptStructureDTO.SlideDTO> slides = new ArrayList<>(sorted.size());
        for (BulkSynthesisRequest.SlideData s : sorted) {
            PptStructureDTO.SlideDTO sd = new PptStructureDTO.SlideDTO();
            sd.setPageNum(s.getPageNumber());
            sd.setTitle(s.getTitle() != null ? s.getTitle() : "");
            sd.setType(s.getType() != null ? s.getType() : "content");
            sd.setBulletPoints(s.getContentPoints() != null ? s.getContentPoints() : List.of());

            // 页面 narration：标题 + 要点 拼起来 → notes
            StringBuilder notes = new StringBuilder();
            if (s.getTitle() != null && !s.getTitle().isBlank()) notes.append(s.getTitle()).append("。\n");
            if (s.getContentPoints() != null && !s.getContentPoints().isEmpty()) {
                for (int i = 0; i < s.getContentPoints().size(); i++) {
                    String pt = s.getContentPoints().get(i);
                    if (pt == null || pt.isBlank()) continue;
                    notes.append(i + 1).append("、").append(pt);
                    if (!pt.endsWith("。") && !pt.endsWith("！") && !pt.endsWith("？")
                            && !pt.endsWith(".") && !pt.endsWith("!") && !pt.endsWith("?")) {
                        notes.append("。");
                    }
                    notes.append("\n");
                }
            }
            sd.setNotes(notes.toString().trim());

            // 可选字段：如果 SlideData 提供了对应字段则填充（新 BulkSynthesisRequest 暂时没有，留空）
            sd.setImageSuggestion("");
            sd.setFormula("");
            sd.setHighlightPoints(List.of());
            sd.setInteraction(null);
            slides.add(sd);
        }
        structure.setSlides(slides);
        return structure;
    }

    /** 同 prep_id 幂等：有则 update，无则 insert。courseId 写入 userId 占位（避免非空约束） */
    private void saveOrUpdateTeachingContent(long prepId, String title, String pptJson, String courseId) {
        TeachingContent exist = null;
        try { exist = teachingContentMapper.selectByPrepId(prepId); } catch (Exception ignore) {}
        if (exist != null) {
            exist.setPptStructure(pptJson);
            if (title != null) exist.setTitle(title);
            // createdAt 自动填充，不覆盖
            teachingContentMapper.updateById(exist);
            log.debug("[新体系] 更新 teaching_contents id={} prepId={}", exist.getId(), prepId);
        } else {
            TeachingContent nc = TeachingContent.builder()
                    .prepId(prepId)
                    .userId(courseIdToUserId(courseId))
                    .title(title != null ? title : "未命名PPT")
                    .status("published")
                    .pptStructure(pptJson)
                    .build();
            teachingContentMapper.insert(nc);
            log.debug("[新体系] 插入 teaching_contents id={} prepId={}", nc.getId(), prepId);
        }
    }

    private Long courseIdToUserId(String courseId) {
        // userId 占位：如果课程里不带用户，用 0 或取 courseId 的 hash 做稳定值；
        // 项目有全局默认用户 ID 时可在这里换成配置值
        if (courseId == null) return 0L;
        return Math.abs((long) courseId.hashCode()) % 9_999_999L + 1L;
    }

    /** 从 PptStructureDTO 收集 NarrationTask（跳过空 notes、跳过已有 AUDIO_URL 前缀） */
    private List<TtsBatchServiceImpl.NarrationTask> collectNarrationTasks(PptStructureDTO structure) {
        if (structure.getSlides() == null) return List.of();
        List<TtsBatchServiceImpl.NarrationTask> tasks = new ArrayList<>();
        for (int i = 0; i < structure.getSlides().size(); i++) {
            PptStructureDTO.SlideDTO s = structure.getSlides().get(i);
            String n = s.getNotes();
            if (n == null || n.isBlank()) continue;
            if (n.startsWith("[AUDIO_URL:")) continue;
            tasks.add(new TtsBatchServiceImpl.NarrationTask(i, n, s.getTitle()));
        }
        return tasks;
    }

    /** 将生成的 {pageIndex, audioUrl} 回填到 structure.slides[i].notes（前缀 + 原文） */
    private void applyAudioUrlsToStructure(PptStructureDTO structure, Map<Integer, String> audioUrls) {
        if (structure.getSlides() == null || audioUrls == null || audioUrls.isEmpty()) return;
        for (Map.Entry<Integer, String> e : audioUrls.entrySet()) {
            int idx = e.getKey();
            String url = e.getValue();
            if (idx < 0 || idx >= structure.getSlides().size()) continue;
            PptStructureDTO.SlideDTO s = structure.getSlides().get(idx);
            String notes = s.getNotes() == null ? "" : s.getNotes();
            String stripped;
            if (notes.startsWith("[AUDIO_URL:")) {
                int newLine = notes.indexOf('\n');
                stripped = newLine < 0 ? "" : notes.substring(newLine + 1).trim();
            } else {
                stripped = notes;
            }
            s.setNotes("[AUDIO_URL:" + url + "]\n" + stripped);
        }
    }

    /** 给 lesson_session.original_text 生成全文（保持原有展示列表的搜索能力） */
    private String originalTextFromStructure(PptStructureDTO s) {
        if (s.getSlides() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (PptStructureDTO.SlideDTO sd : s.getSlides()) {
            int pn = sd.getPageNum() != null ? sd.getPageNum() : sb.toString().split("\n第").length;
            sb.append("第").append(pn).append("页: ");
            if (sd.getTitle() != null) sb.append(sd.getTitle());
            sb.append("\n");
            if (sd.getBulletPoints() != null) {
                for (String p : sd.getBulletPoints()) sb.append("- ").append(p).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public List<PPTAudioSegment> getPageAudioSegments(String courseId, Integer pageNumber) {
        log.info("[新体系] 获取页面音频片段，会话ID: {}, 页码: {}", courseId, pageNumber);
        // 统一走新体系 NarrationBridge（内部读 teaching_contents.slides[].notes [AUDIO_URL:]）
        return narrationBridgeService.getSegments(courseId, pageNumber);
    }

    @Override
    public PPTAudioSegment getAudioSegment(String courseId, Integer segmentIndex) {
        log.warn("[新体系] 不支持按全局片段索引取单条（1页=1段，索引=pageNumber-1），courseId={} segmentIndex={}",
                courseId, segmentIndex);
        // 兼容：1段=1页 → 把 segmentIndex 当 pageNumber-1 查，没找到返回 null
        List<PPTAudioSegment> page = getPageAudioSegments(courseId, segmentIndex + 1);
        return (page != null && !page.isEmpty()) ? page.get(0) : null;
    }

    @Override
    public PPTAudioInfo getPPTAudioInfo(String courseId) {
        log.info("[新体系] 获取PPT音频信息，会话ID: {}", courseId);
        // 统一走新体系 NarrationBridge 统计
        return narrationBridgeService.getInfo(courseId);
    }

    //1
    @Override
    public BulkPreprocessingResponse processBulkPreprocessing(BulkSynthesisRequest request) {
        return processBulkPreprocessing(request, null);
    }

    @Override
    public BulkPreprocessingResponse processBulkPreprocessing(BulkSynthesisRequest request, Long userId) {
        // 【新体系 · 预处理】
        // 只把 PPT 内容转 PptStructureDTO → 写入 teaching_contents(prep_id=courseId)，不调 TTS。
        // 仍然把 lesson_session 置为 PENDING_REVIEW，给现有管理后台的"待审核列表"保持一致展示。
        String courseId = Optional.ofNullable(request.getCourseId())
                .filter(s -> !s.isBlank()).orElse(generateCourseId());
        long prepIdN = courseIdToPrepId(courseId);
        LocalDateTime startTime = LocalDateTime.now();
        log.info("[新体系] 批量文本预处理，courseId={} prepId={} title={} slides={}",
                courseId, prepIdN, request.getTitle(),
                request.getSlides() != null ? request.getSlides().size() : 0);

        try {
            List<BulkSynthesisRequest.SlideData> sorted = request.getSlides() == null ? List.of()
                    : request.getSlides().stream()
                    .filter(s -> s != null && s.getPageNumber() != null)
                    .sorted(Comparator.comparing(BulkSynthesisRequest.SlideData::getPageNumber))
                    .collect(Collectors.toList());
            if (sorted.isEmpty()) {
                return BulkPreprocessingResponse.builder()
                        .courseId(courseId).status("FAILED").message("slides 为空")
                        .startTime(startTime).endTime(LocalDateTime.now()).build();
            }

            PptStructureDTO structure = convertBulkRequestToStructure(request, sorted);
            String json = objectMapper.writeValueAsString(structure);
            saveOrUpdateTeachingContent(prepIdN, request.getTitle(), json, courseId);

            int totalTextSegments = sorted.size();   // 1 页 = 1 段 narration

            // 保留 lesson_session 展示记录
            try {
                if (voiceDatabaseService.getCompleteSessionInfo(courseId) == null) {
                    String original = originalTextFromStructure(structure);
                    voiceDatabaseService.createCompleteSession(
                            courseId, request.getTitle(), original, null, new ArrayList<>());
                }
                updateSessionStatus(courseId, "DRAFT", null, null, null);
                updateSessionStatus(courseId, "PENDING_REVIEW", null, null, null);
            } catch (Exception e) {
                log.warn("创建 lesson_session 展示记录失败(不影响新体系主链路) courseId={} err={}",
                        courseId, e.getMessage());
            }

            return BulkPreprocessingResponse.builder()
                    .courseId(courseId)
                    .status("SUCCESS")
                    .totalSlides(sorted.size())
                    .totalTextSegments(totalTextSegments)
                    .message("新体系文本预处理完成，等待审核后执行合成")
                    .startTime(startTime)
                    .endTime(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("[新体系] 批量预处理失败 courseId={}", courseId, e);
            return BulkPreprocessingResponse.builder()
                    .courseId(courseId).status("FAILED")
                    .message("处理失败: " + e.getMessage())
                    .startTime(startTime).endTime(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public BulkSynthesisResponse executeBulkSynthesis(String courseId, String userJwt) {
        LocalDateTime startTime = LocalDateTime.now();
        long prepIdN = courseIdToPrepId(courseId);
        log.info("[新体系] 审核后执行合成，courseId={} prepId={} userJwt 存在={}",
                courseId, prepIdN, userJwt != null && !userJwt.isBlank());

        try {
            LessonSession session = voiceDatabaseService.getCompleteSessionInfo(courseId);
            if (session != null && !"APPROVED".equals(session.getProcessingStatus())) {
                log.warn("[新体系] lesson_session 状态未 APPROVED，仍执行合成（新体系不依赖老状态机），status={}",
                        session.getProcessingStatus());
            }

            TeachingContent tc = teachingContentMapper.selectByPrepId(prepIdN);
            if (tc == null || tc.getPptStructure() == null || tc.getPptStructure().isBlank()) {
                return BulkSynthesisResponse.builder()
                        .courseId(courseId).status("FAILED")
                        .message("未找到 PPT 结构数据，请先执行预处理").startTime(startTime).build();
            }
            PptStructureDTO structure = PptStructureDTO.parse(objectMapper, tc.getPptStructure());
            if (structure.getSlides() == null || structure.getSlides().isEmpty()) {
                return BulkSynthesisResponse.builder()
                        .courseId(courseId).status("FAILED")
                        .message("PPT 结构 slides 为空").startTime(startTime).build();
            }

            int success = ttsBatchService.generateAndBackfill(structure, prepIdN, userJwt, p -> {
                log.debug("[新体系] 执行合成进度 {}/{} slide={} status={}",
                        p.getCurrentIndex(), p.getTotalCount(), p.getCurrentTitle(), p.getStatus());
            });

            try { updateSessionStatus(courseId, "SYNTHESIZED", null, null, null); } catch (Exception ignore) {}

            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("COMPLETED")
                    .totalSlides(structure.getSlides().size())
                    .totalContentPoints(success)
                    .message(success == 0
                            ? "无待合成片段（已全部生成过或原文为空）"
                            : String.format("执行合成完成，成功 %d 条", success))
                    .startTime(startTime)
                    .build();

        } catch (Exception e) {
            log.error("[新体系] 执行合成失败 courseId={}", courseId, e);
            return BulkSynthesisResponse.builder()
                    .courseId(courseId).status("FAILED")
                    .message("执行合成失败: " + e.getMessage())
                    .startTime(startTime).build();
        }
    }

    @Override
    public List<LessonSession> getPendingReviewSessions() {
        log.info("获取待审核会话列表");
        return voiceDatabaseService.getSessionsByStatus("PENDING_REVIEW");
    }

    @Override
    public List<LessonSession> getSessionsByStatus(String status) {
        log.info("根据状态获取会话列表，状态: {}", status);
        if (status == null || status.trim().isEmpty()) {
            // 如果状态为空，返回所有会话（这里需要扩展VoiceDatabaseService）
            return voiceDatabaseService.getSessionsByStatus(null);
        }
        return voiceDatabaseService.getSessionsByStatus(status);
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<LessonSession> getSessionsByStatusPage(String status, long page, long size) {
        log.info("分页获取会话列表，状态: {}, 页码: {}, 每页: {}", status, page, size);
        return voiceDatabaseService.getSessionsByStatusPage(status, page, size);
    }

    @Override
    public ReviewResponse reviewSession(String courseId, String reviewerId, Boolean approved, String comments) {
        log.info("审核会话，会话ID: {}, 审核人: {}, 结果: {}", courseId, reviewerId, approved);

        try {
            // 检查会话是否存在
            LessonSession session = voiceDatabaseService.getCompleteSessionInfo(courseId);
            if (session == null) {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("会话不存在")
                        .build();
            }

            if (!"PENDING_REVIEW".equals(session.getProcessingStatus())) {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("会话状态不正确，当前状态: " + session.getProcessingStatus())
                        .build();
            }

            // 更新审核状态
            String newStatus = approved ? "APPROVED" : "REJECTED";
            boolean updated = updateSessionStatus(courseId, newStatus, reviewerId, LocalDateTime.now(), comments);

            if (updated) {
                log.info("会话审核完成，会话ID: {}, 新状态: {}", courseId, newStatus);
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("SUCCESS")
                        .message("审核完成")
                        .newStatus(newStatus)
                        .build();
            } else {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("更新审核状态失败")
                        .build();
            }

        } catch (Exception e) {
            log.error("审核会话失败，会话ID: {}", courseId, e);
            return ReviewResponse.builder()
                    .courseId(courseId)
                    .status("FAILED")
                    .message("审核失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public ReviewResponse adminReviewSession(String courseId, AdminReviewRequest request) {
        log.info("管理员审核会话，会话ID: {}, 审核人: {}, 结果: {}, 是否修改文本: {}",
                courseId, request.getReviewerId(), request.getApproved(),
                request.getUpdatedPolishedText() != null);

        try {
            // 检查会话是否存在
            LessonSession session = voiceDatabaseService.getCompleteSessionInfo(courseId);
            if (session == null) {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("会话不存在")
                        .build();
            }

            if (!"PENDING_REVIEW".equals(session.getProcessingStatus())) {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("会话状态不正确，当前状态: " + session.getProcessingStatus())
                        .build();
            }

            // 1. 更新会话基本信息（如果提供）
            boolean sessionUpdated = false;

            // 更新标题
            if (request.getUpdatedTitle() != null && !request.getUpdatedTitle().trim().isEmpty()) {
                boolean titleUpdated = voiceDatabaseService.updateSessionTitle(courseId, request.getUpdatedTitle());
                if (titleUpdated) {
                    log.info("会话标题更新成功，会话ID: {}", courseId);
                    sessionUpdated = true;
                } else {
                    log.warn("会话标题更新失败，会话ID: {}", courseId);
                }
            }

            // 更新润色文本
            if (request.getUpdatedPolishedText() != null && !request.getUpdatedPolishedText().trim().isEmpty()) {
                boolean textUpdated = voiceDatabaseService.updateSessionPolishedText(courseId, request.getUpdatedPolishedText());
                if (textUpdated) {
                    log.info("会话润色文本更新成功，会话ID: {}, 文本长度: {}", courseId, request.getUpdatedPolishedText().length());
                    sessionUpdated = true;

                    // 2. 如果需要强制更新文本片段
                    if (Boolean.TRUE.equals(request.getForceUpdateSegments())) {
                        try {
                            boolean segmentsUpdated = updateTextSegmentsFromPolishedText(courseId, request.getUpdatedPolishedText());
                            if (segmentsUpdated) {
                                log.info("文本片段更新成功，会话ID: {}", courseId);
                            } else {
                                log.warn("文本片段更新失败，会话ID: {}", courseId);
                            }
                        } catch (Exception e) {
                            log.error("更新文本片段时发生异常，会话ID: {}", courseId, e);
                        }
                    }
                } else {
                    log.warn("会话润色文本更新失败，会话ID: {}", courseId);
                }
            }

            // 3. 更新审核状态
            String newStatus = request.getApproved() ? "APPROVED" : "REJECTED";
            boolean statusUpdated = updateSessionStatus(courseId, newStatus, request.getReviewerId(),
                    LocalDateTime.now(), request.getComments());

            if (statusUpdated) {
                log.info("管理员会话审核完成，会话ID: {}, 新状态: {}, 会话信息更新: {}",
                        courseId, newStatus, sessionUpdated);

                String message = sessionUpdated ? "审核完成，会话信息已更新" : "审核完成";

                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("SUCCESS")
                        .message(message)
                        .newStatus(newStatus)
                        .build();
            } else {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("更新审核状态失败")
                        .build();
            }

        } catch (Exception e) {
            log.error("管理员审核会话失败，会话ID: {}", courseId, e);
            return ReviewResponse.builder()
                    .courseId(courseId)
                    .status("FAILED")
                    .message("审核失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public LessonSession getPendingReviewSessionsByCourseId(String courseId) {
        LessonSession lessonSession = lessonSessionService.getByCourseId(courseId);
        return lessonSession;
    }

    /**
     * 处理单个slide的文本预处理并返回润色文本（新方法）
     */
    private SlideTextProcessResult processSlideTextOnlyWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                                      BulkSynthesisRequest.BulkSynthesisOptions options) {

        log.info("开始处理页面文本，会话ID: {}, 页码: {}, 内容点数: {}",
                courseId, slide.getPageNumber(), slide.getContentPoints().size());

        List<PPTAudioSegment> textSegments = new ArrayList<>();
        String slidePolishedText = "";
        int segmentCount = 0;

        // 检查是否为目录页面
        if (isAgendaOrTitlePage(slide)) {
            SlideTextProcessResult result = processAgendaSlideTextOnlyWithPolishedText(courseId, slide, options, textSegments);
            segmentCount = result.getSegmentCount();
            slidePolishedText = result.getPolishedText();
        } else {
            SlideTextProcessResult result = processNormalSlideTextOnlyWithPolishedText(courseId, slide, options, textSegments);
            segmentCount = result.getSegmentCount();
            slidePolishedText = result.getPolishedText();
        }

        // 保存文本片段到数据库（不包含音频数据）
        if (!textSegments.isEmpty()) {
            try {
                boolean saved = pageLevelAudioService.saveTextOnlySegments(
                        courseId,
                        slide.getPageNumber(),
                        slide.getTitle(),
                        slide.getSlideType(),
                        slide.getDescription(),
                        textSegments
                );

                if (saved) {
                    log.info("页面文本片段保存成功，页码: {}, 片段数: {}", slide.getPageNumber(), textSegments.size());
                } else {
                    log.error("页面文本片段保存失败，页码: {}", slide.getPageNumber());
                }
            } catch (Exception e) {
                log.error("保存页面文本片段时发生异常，页码: {}", slide.getPageNumber(), e);
            }
        }

        return SlideTextProcessResult.builder()
                .segmentCount(segmentCount)
                .polishedText(slidePolishedText)
                .build();
    }

    /**
     * 处理目录页面的文本预处理并返回润色文本
     */
    private SlideTextProcessResult processAgendaSlideTextOnlyWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                                            BulkSynthesisRequest.BulkSynthesisOptions options,
                                                                            List<PPTAudioSegment> textSegments) {

        // 合并所有内容点为一个字符串
        String combinedContent = String.join(" ", slide.getContentPoints());
        String finalPolishedText = combinedContent;

        try {
            // 1. 文本润色（如果启用）
            if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                try {
                    String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, combinedContent, 0);
                    Integer hardCap = 200; // 目录页面固定200字限制

                    String polishedText = aiModelService.polishTextWithPrompt(combinedContent, customPrompt, hardCap).block(Duration.ofSeconds(25));
                    if (polishedText != null && !polishedText.trim().isEmpty()) {
                        finalPolishedText = polishedText;
                    }
                } catch (Exception e) {
                    log.warn("目录页面润色失败，使用原文，页码: {}", slide.getPageNumber(), e);
                }
            }

            // 2. 分句处理
            List<String> sentences = segmentedSpeechService.splitTextBySentence(finalPolishedText);
            if (sentences.isEmpty()) {
                return SlideTextProcessResult.builder()
                        .segmentCount(0)
                        .polishedText(finalPolishedText)
                        .build();
            }

            // 3. 创建文本片段（不包含音频数据）
            for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                String sentence = sentences.get(sentenceIndex);
                int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                        slide.getPageNumber(), 0, sentenceIndex);

                PPTAudioSegment textSegment = PPTAudioSegment.builder()
                        .courseId(courseId)
                        .slidePageNumber(slide.getPageNumber())
                        .slideTitle(slide.getTitle())
                        .contentPointIndex(0)
                        .segmentIndex(globalSegmentIndex)
                        .slideType(slide.getSlideType())
                        .slideDescription(slide.getDescription())
                        .originalText(combinedContent)
                        .polishedText(finalPolishedText)
                        .textContent(sentence)
                        .audioData(null) // 不包含音频数据
                        .audioSize(0L)
                        .duration(0L)
                        .audioFormat(options != null ? options.getAudioFormat() : "wav")
                        .sampleRate(options != null ? options.getSampleRate() : 16000)
                        .createdAt(LocalDateTime.now())
                        .build();

                textSegments.add(textSegment);
            }

            return SlideTextProcessResult.builder()
                    .segmentCount(sentences.size())
                    .polishedText(finalPolishedText)
                    .build();

        } catch (Exception e) {
            log.error("处理目录页面文本失败，页码: {}", slide.getPageNumber(), e);
            return SlideTextProcessResult.builder()
                    .segmentCount(0)
                    .polishedText(combinedContent)
                    .build();
        }
    }

    /**
     * 处理普通页面的文本预处理并返回润色文本
     */
    private SlideTextProcessResult processNormalSlideTextOnlyWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                                            BulkSynthesisRequest.BulkSynthesisOptions options,
                                                                            List<PPTAudioSegment> textSegments) {

        int segmentCount = 0;
        StringBuilder slidePolishedText = new StringBuilder();

        for (int pointIndex = 0; pointIndex < slide.getContentPoints().size(); pointIndex++) {
            String contentPoint = slide.getContentPoints().get(pointIndex);

            if (contentPoint == null || contentPoint.trim().isEmpty()) {
                continue;
            }

            try {
                // 1. 文本润色
                String polishedText = contentPoint;
                if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                    try {
                        String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, contentPoint, pointIndex);
                        int originalLength = contentPoint.length();
                        Integer hardCap = originalLength + 200;

                        String result = aiModelService.polishTextWithPrompt(contentPoint, customPrompt, hardCap).block(Duration.ofSeconds(25));
                        if (result != null && !result.trim().isEmpty()) {
                            polishedText = result;
                        }
                    } catch (Exception e) {
                        log.warn("文本润色失败，使用原文，页码: {}, 内容点索引: {}",
                                slide.getPageNumber(), pointIndex, e);
                    }
                }

                // 收集润色后的文本
                if (slidePolishedText.length() > 0) {
                    slidePolishedText.append("\n");
                }
                slidePolishedText.append("- ").append(polishedText);

                // 2. 分句处理
                List<String> sentences = segmentedSpeechService.splitTextBySentence(polishedText);
                if (sentences.isEmpty()) {
                    continue;
                }

                // 3. 创建文本片段
                for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                    String sentence = sentences.get(sentenceIndex);
                    int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                            slide.getPageNumber(), pointIndex, sentenceIndex);

                    PPTAudioSegment textSegment = PPTAudioSegment.builder()
                            .courseId(courseId)
                            .slidePageNumber(slide.getPageNumber())
                            .slideTitle(slide.getTitle())
                            .contentPointIndex(pointIndex)
                            .segmentIndex(globalSegmentIndex)
                            .slideType(slide.getSlideType())
                            .slideDescription(slide.getDescription())
                            .originalText(contentPoint)
                            .polishedText(polishedText)
                            .textContent(sentence)
                            .audioData(null) // 不包含音频数据
                            .audioSize(0L)
                            .duration(0L)
                            .audioFormat(options != null ? options.getAudioFormat() : "wav")
                            .sampleRate(options != null ? options.getSampleRate() : 16000)
                            .createdAt(LocalDateTime.now())
                            .build();

                    textSegments.add(textSegment);
                    segmentCount++;
                }

            } catch (Exception e) {
                log.error("处理内容点文本失败，页码: {}, 内容点索引: {}", slide.getPageNumber(), pointIndex, e);
            }
        }

        return SlideTextProcessResult.builder()
                .segmentCount(segmentCount)
                .polishedText(slidePolishedText.toString())
                .build();
    }

    /**
     * 为页面的文本片段生成音频
     */
    private int synthesizePageAudio(String courseId, Integer pageNumber, List<AudioSegment> textSegments) {
        log.info("开始为页面生成音频，会话ID: {}, 页码: {}, 片段数: {}", courseId, pageNumber, textSegments.size());

        List<PPTAudioSegment> audioSegments = new ArrayList<>();
        int successCount = 0;

        for (AudioSegment textSegment : textSegments) {
            try {
                // 语音合成 — 含穿透/击穿/雪崩防护
                String textHash = BulkSpeechCache.textHash(textSegment.getTextContent());
                byte[] audioData = bulkSpeechCache.getCachedAudio(textHash);

                if (audioData == null) {
                    boolean rebuildLocked = bulkSpeechCache.tryAcquireRebuildLock(textHash);
                    if (rebuildLocked) {
                        try {
                            audioData = bulkSpeechCache.getCachedAudio(textHash); // 二次检查
                            if (audioData == null) {
                                try {
                                    Mono<byte[]> synthesisResult = textToSpeechService.synthesizeSpeech(textSegment.getTextContent());
                                    audioData = synthesisResult.block(Duration.ofSeconds(30));
                                    if (audioData == null || audioData.length == 0) {
                                        log.error("TTS合成结果为空，片段: {}", textSegment.getSegmentIndex());
                                        bulkSpeechCache.putCachedAudioNull(textHash);
                                        continue;
                                    }
                                } catch (Exception ttsError) {
                                    log.error("TTS合成失败，片段: {}", textSegment.getSegmentIndex(), ttsError);
                                    bulkSpeechCache.putCachedAudioNull(textHash);
                                    continue;
                                }
                                bulkSpeechCache.putCachedAudio(textHash, audioData);
                            }
                        } finally {
                            bulkSpeechCache.releaseRebuildLock(textHash);
                        }
                    } else {
                        audioData = bulkSpeechCache.spinWaitForRebuild(textHash);
                        if (audioData == null) {
                            log.warn("自旋等待重建失败，跳过片段: {}", textSegment.getSegmentIndex());
                            continue;
                        }
                    }
                }

                long duration = estimateAudioDuration(audioData);

                // 创建音频片段对象
                PPTAudioSegment audioSegment = PPTAudioSegment.builder()
                        .courseId(courseId)
                        .slidePageNumber(pageNumber)
                        .slideTitle(textSegment.getSlideTitle())
                        .contentPointIndex(0) // 从textSegment中获取
                        .segmentIndex(textSegment.getSegmentIndex())
                        .slideType(textSegment.getSlideType())
                        .slideDescription(textSegment.getSlideDescription())
                        .originalText(textSegment.getOriginalText())
                        .polishedText(textSegment.getPolishedText())
                        .textContent(textSegment.getTextContent())
                        .audioData(audioData)
                        .audioSize((long) audioData.length)
                        .duration(duration)
                        .audioFormat(textSegment.getAudioFormat())
                        .sampleRate(textSegment.getSampleRate())
                        .checksum(generateChecksum(audioData))
                        .createdAt(LocalDateTime.now())
                        .build();

                audioSegments.add(audioSegment);
                successCount++;

            } catch (Exception e) {
                log.error("为文本片段生成音频失败，片段索引: {}", textSegment.getSegmentIndex(), e);
            }
        }

        // 保存音频数据并更新状态
        if (!audioSegments.isEmpty()) {
            try {
                boolean saved = pageLevelAudioService.updateSegmentsWithAudio(courseId, pageNumber, audioSegments);
                if (saved) {
                    log.info("页面音频更新成功，页码: {}, 成功片段数: {}", pageNumber, successCount);
                } else {
                    log.error("页面音频更新失败，页码: {}", pageNumber);
                }
            } catch (Exception e) {
                log.error("更新页面音频时发生异常，页码: {}", pageNumber, e);
            }
        }

        return successCount;
    }

    /**
     * 根据润色文本更新文本片段
     * 这个方法会重新分句并更新数据库中的文本片段
     */
    private boolean updateTextSegmentsFromPolishedText(String courseId, String polishedText) {
        log.info("开始根据润色文本更新文本片段，会话ID: {}, 文本长度: {}", courseId, polishedText.length());

        try {
            // 1. 获取会话信息
            LessonSession session = voiceDatabaseService.getCompleteSessionInfo(courseId);
            if (session == null) {
                log.error("会话不存在，无法更新文本片段，会话ID: {}", courseId);
                return false;
            }

            // 2. 删除现有的文本片段（TEXT_ONLY状态的）
            boolean deleted = pageLevelAudioService.deleteTextOnlySegments(courseId);
            if (!deleted) {
                log.warn("删除现有文本片段失败，会话ID: {}", courseId);
            }

            // 3. 按页面分割润色文本
            // 假设润色文本格式为：第X页: 标题\n内容\n\n
            String[] pageTexts = polishedText.split("第\\d+页:");

            for (int i = 1; i < pageTexts.length; i++) { // 跳过第一个空元素
                String pageText = pageTexts[i].trim();
                if (pageText.isEmpty()) continue;

                // 解析页面信息
                String[] lines = pageText.split("\n", 2);
                if (lines.length < 2) continue;

                String pageTitle = lines[0].trim();
                String pageContent = lines[1].trim();

                // 分句处理
                List<String> sentences = segmentedSpeechService.splitTextBySentence(pageContent);

                // 创建新的文本片段
                List<PPTAudioSegment> textSegments = new ArrayList<>();
                for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                    String sentence = sentences.get(sentenceIndex);

                    // 使用页面索引作为全局片段索引的基础
                    int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(i, 0, sentenceIndex);

                    PPTAudioSegment textSegment = PPTAudioSegment.builder()
                            .courseId(courseId)
                            .slidePageNumber(i)
                            .slideTitle(pageTitle)
                            .contentPointIndex(0)
                            .segmentIndex(globalSegmentIndex)
                            .slideType("CONTENT") // 默认类型
                            .slideDescription("")
                            .originalText(pageContent)
                            .polishedText(pageContent)
                            .textContent(sentence)
                            .audioData(null) // 不包含音频数据
                            .audioSize(0L)
                            .duration(0L)
                            .audioFormat("wav")
                            .sampleRate(16000)
                            .createdAt(LocalDateTime.now())
                            .build();

                    textSegments.add(textSegment);
                }

                // 保存页面的文本片段
                if (!textSegments.isEmpty()) {
                    boolean saved = pageLevelAudioService.saveTextOnlySegments(
                            courseId, i, pageTitle, "CONTENT", "", textSegments);

                    if (saved) {
                        log.info("页面文本片段更新成功，页码: {}, 片段数: {}", i, textSegments.size());
                    } else {
                        log.error("页面文本片段更新失败，页码: {}", i);
                    }
                }
            }

            log.info("文本片段更新完成，会话ID: {}", courseId);
            return true;

        } catch (Exception e) {
            log.error("根据润色文本更新文本片段失败，会话ID: {}", courseId, e);
            return false;
        }
    }

    /**
     * 更新会话状态
     */
    private boolean updateSessionStatus(String courseId, String status, String reviewerId,
                                      LocalDateTime reviewedAt, String comments) {
        try {
            return voiceDatabaseService.updateSessionStatus(courseId, status, reviewerId, reviewedAt, comments);
        } catch (Exception e) {
            log.error("更新会话状态失败，会话ID: {}, 状态: {}", courseId, status, e);
            return false;
        }
    }

}
