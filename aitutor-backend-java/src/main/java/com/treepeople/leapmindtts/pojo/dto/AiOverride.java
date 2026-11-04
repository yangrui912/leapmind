package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;

/**
 * 用户自填的 AI 服务配置（不落库、不打日志）。
 * 由前端在专用请求头中传入，随 AI 调用透传到 Python 服务，
 * 仅在本次请求内生效；未填写时回退到服务端环境变量配置。
 */
@Data
public class AiOverride {

    /** OpenAI 兼容接口的 base url，例如 https://host/v1 */
    private String baseUrl;

    /** 用户自己的 API Key（禁止写入日志） */
    private String apiKey;

    /** 模型标识 */
    private String model;
}
