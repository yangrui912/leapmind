/**
 * 用户自填的 AI 服务配置
 *
 * 设计原则：
 * - 只保存在浏览器（localStorage 或 sessionStorage），不发送给后端保存；
 * - 随 AI 相关请求以请求头透传，服务端仅在本次请求内使用，不落库、不写日志；
 * - 默认不内置任何服务地址或访问令牌。
 */

const STORAGE_KEY = 'leapmind_ai_config';
const SESSION_KEY = 'leapmind_ai_config_session';

const HEADER_BASE_URL = 'X-Ai-Base-Url';
const HEADER_TOKEN = 'X-Ai-Token';
const HEADER_MODEL = 'X-Ai-Model';
const HEADER_VISION_MODEL = 'X-Ai-Vision-Model';

function safeParse(raw) {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') return null;
    return parsed;
  } catch {
    return null;
  }
}

/**
 * 读取当前生效的 AI 配置（会话级优先于持久化级别）
 * @returns {{baseUrl?: string, token?: string, model?: string, visionModel?: string, sessionOnly?: boolean}|null}
 */
export function loadAiSettings() {
  try {
    const sessionConfig = safeParse(sessionStorage.getItem(SESSION_KEY));
    if (sessionConfig) return { ...sessionConfig, sessionOnly: true };
    const saved = safeParse(localStorage.getItem(STORAGE_KEY));
    return saved ? { ...saved, sessionOnly: false } : null;
  } catch {
    return null;
  }
}

/**
 * 保存 AI 配置
 * @param {{baseUrl: string, token: string, model?: string, visionModel?: string}} config
 * @param {{sessionOnly?: boolean}} options 仅本次会话时写入 sessionStorage
 */
export function saveAiSettings(config, options = {}) {
  const payload = {
    baseUrl: (config.baseUrl || '').trim(),
    token: (config.token || '').trim(),
    model: (config.model || '').trim(),
    visionModel: (config.visionModel || '').trim(),
  };
  clearAiSettings();
  if (options.sessionOnly) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(payload));
  } else {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  }
  return payload;
}

/** 清除已保存的 AI 配置 */
export function clearAiSettings() {
  try {
    localStorage.removeItem(STORAGE_KEY);
    sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // 忽略存储不可用的情况
  }
}

/** 是否已配置可用的 AI 服务 */
export function hasAiSettings() {
  const config = loadAiSettings();
  return Boolean(config && config.baseUrl && config.token);
}

/**
 * 生成 AI 相关请求头；未配置时返回空对象
 * visionModel 单独透传（拍照搜题的图片识别用），未填时由后端回退到主模型
 */
export function getAiHeaders() {
  const config = loadAiSettings();
  if (!config || !config.baseUrl || !config.token) return {};
  const headers = {
    [HEADER_BASE_URL]: config.baseUrl,
    [HEADER_TOKEN]: config.token,
  };
  if (config.model) {
    headers[HEADER_MODEL] = config.model;
  }
  if (config.visionModel) {
    headers[HEADER_VISION_MODEL] = config.visionModel;
  }
  return headers;
}

export default {
  loadAiSettings,
  saveAiSettings,
  clearAiSettings,
  hasAiSettings,
  getAiHeaders,
};
