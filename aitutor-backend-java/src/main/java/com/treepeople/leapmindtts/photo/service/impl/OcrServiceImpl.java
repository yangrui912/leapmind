package com.treepeople.leapmindtts.photo.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baidu.aip.ocr.AipOcr;
import com.treepeople.leapmindtts.photo.config.OcrConfig;
import com.treepeople.leapmindtts.photo.dto.OcrResultDTO;
import com.treepeople.leapmindtts.photo.exception.BusinessException;
import com.treepeople.leapmindtts.photo.service.OcrService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.util.Base64;
import java.util.HashMap;

/**
 * OCR 文字识别服务实现类
 * 支持百度OCR和阿里云OCR两种方式，通过配置切换
 */
@Service
public class OcrServiceImpl implements OcrService {
    @Resource
    private OcrConfig ocrConfig;

    @Resource
    private com.treepeople.leapmindtts.service.ai.AiOverrideResolver aiOverrideResolver;

    @Resource
    private com.treepeople.leapmindtts.service.explain.PythonAIClient pythonAIClient;

    @org.springframework.beans.factory.annotation.Value("${python.ai.base-url:http://localhost:8001}")
    private String pythonAiBaseUrl;

    @Override
    public OcrResultDTO recognize(MultipartFile file) {
        long start = System.currentTimeMillis();

        // 用户自填了 AI 服务时，优先用其视觉模型识别（无需额外 OCR 凭据）
        com.treepeople.leapmindtts.pojo.dto.AiOverride override = aiOverrideResolver.current();
        if (override != null) {
            OcrResultDTO dto = new OcrResultDTO();
            dto.setProvider("vision");
            try {
                return visionOcr(file, override, dto, start);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                String detail = e.getMessage() == null ? "" : e.getMessage();
                detail = detail.replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "sk-***");
                if (detail.length() > 200) {
                    detail = detail.substring(0, 200);
                }
                throw new BusinessException(502, "图片识别失败：" + (detail.isBlank() ? "请检查 AI 服务配置" : detail));
            }
        }

        String provider = ocrConfig.getProvider();
        OcrResultDTO dto = new OcrResultDTO();
        dto.setProvider(provider);

        try {
            // 根据配置选择OCR服务商
            if ("baidu".equals(provider)) {
                return baiduOcr(file, dto, start);
            } else if ("aliyun".equals(provider)) {
                return aliyunOcr(file, dto, start);
            } else {
                throw new BusinessException(400, "不支持的OCR服务商：" + provider);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "OCR识别异常：" + e.getMessage());
        }
    }

    /**
     * 视觉模型识别：把图片交给用户自填的多模态模型，返回题目文字
     */
    private OcrResultDTO visionOcr(MultipartFile file,
                                   com.treepeople.leapmindtts.pojo.dto.AiOverride override,
                                   OcrResultDTO dto, long start) throws Exception {
        String contentType = file.getContentType();
        String mime = (contentType == null || contentType.isBlank()) ? "image/png" : contentType;
        String imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("imageBase64", imageBase64);
        payload.put("mimeType", mime);
        payload.put("aiOverride", override);

        String url = pythonAiBaseUrl + "/api/internal/ai/ocr/vision";
        String response = pythonAIClient.callRaw(url, payload);

        JSONObject json = JSONUtil.parseObj(response);
        if (!json.getBool("success", false)) {
            String reason = json.getStr("error", "模型识别失败");
            throw new BusinessException(502, "图片识别失败：" + reason);
        }
        String text = json.getStr("text", "");
        if (text == null || text.isBlank()) {
            throw new BusinessException(422, "未识别到文字，请上传更清晰的题目图片");
        }
        dto.setText(text);
        dto.setWordsCount(text.replaceAll("\\s+", "").length());
        dto.setCostTime(System.currentTimeMillis() - start);
        return dto;
    }

    /**
     * 百度OCR识别
     */
    private OcrResultDTO baiduOcr(MultipartFile file, OcrResultDTO dto, long start) throws Exception {
        OcrConfig.BaiduOcr baidu = ocrConfig.getBaidu();
        AipOcr client = new AipOcr(baidu.getAppId(), baidu.getApiKey(), baidu.getSecretKey());

        HashMap<String, String> options = new HashMap<>();
        options.put("detect_direction", "true");

        org.json.JSONObject res = client.basicAccurateGeneral(file.getBytes(), options);
        StringBuilder sb = new StringBuilder();

        res.getJSONArray("words_result").forEach(o -> {
            sb.append(((org.json.JSONObject) o).getString("words")).append("\n");
        });

        dto.setText(sb.toString().trim());
        dto.setWordsCount(res.getJSONArray("words_result").length());
        dto.setDirection(res.getInt("direction"));
        dto.setCostTime(System.currentTimeMillis() - start);
        return dto;
    }

    /**
     * 阿里云OCR识别（API市场 AppCode 方式）
     */
    private OcrResultDTO aliyunOcr(MultipartFile file, OcrResultDTO dto, long start) throws Exception {
        OcrConfig.AliOcr aliOcr = ocrConfig.getAliyun();

        // 1. 图片转base64编码
        String imgBase64 = Base64.getEncoder().encodeToString(file.getBytes());

        // 2. 构建请求体
        JSONObject requestBody = JSONUtil.createObj();
        requestBody.set("img", imgBase64);
        requestBody.set("prob", false);
        requestBody.set("charInfo", false);
        requestBody.set("rotate", false);
        requestBody.set("table", false);
        requestBody.set("sortPage", false);
        requestBody.set("noStamp", false);
        requestBody.set("figure", false);
        requestBody.set("row", false);
        requestBody.set("paragraph", false);
        requestBody.set("oricoord", true);

        // 3. 发送HTTP请求调用阿里云OCR接口
        HttpResponse response = HttpRequest.post(aliOcr.getApiUrl())
                .header("Authorization", "APPCODE " + aliOcr.getAppCode())
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(requestBody.toString())
                .timeout(30000)
                .execute();

        if (!response.isOk()) {
            throw new BusinessException(500, "阿里云OCR调用失败，HTTP状态码：" + response.getStatus());
        }

        // 4. 解析返回结果
        JSONObject resultJson = JSONUtil.parseObj(response.body());

        // 检查是否调用成功
        if (resultJson.containsKey("success") && !resultJson.getBool("success")) {
            String message = resultJson.getStr("message", "识别失败");
            throw new BusinessException(500, "阿里云OCR识别失败：" + message);
        }

        // 提取识别出的文字
        JSONArray resultArray = resultJson.getJSONArray("result");
        StringBuilder sb = new StringBuilder();

        if (resultArray != null) {
            for (int i = 0; i < resultArray.size(); i++) {
                JSONObject item = resultArray.getJSONObject(i);
                sb.append(item.getStr("word")).append("\n");
            }
        }

        dto.setText(sb.toString().trim());
        dto.setWordsCount(resultArray != null ? resultArray.size() : 0);
        dto.setDirection(0);
        dto.setCostTime(System.currentTimeMillis() - start);

        return dto;
    }
}
