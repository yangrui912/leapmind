/**
 * 全局 fetch 拦截器
 *
 * 项目里部分模块（m2/m3/m5 的流式接口、文件上传）直接使用 fetch，
 * 绕过了 services/api.js 的统一封装，导致：
 *   1. 不带登录 token（需要鉴权的接口 401）；
 *   2. 不带用户自填的 AI 配置请求头（AI/OCR 功能回退到服务端默认或失败）。
 *
 * 这里在应用启动时对同源 /api/ 请求统一补齐这两类请求头，
 * 不改动 body，也不设置 Content-Type（避免破坏 FormData 上传）。
 */

import { getToken } from '../utils/tokenManager';
import { getAiHeaders } from './aiSettings';

const PATCH_FLAG = '__leapmindFetchPatched';

function resolveUrl(input) {
  if (typeof input === 'string') return input;
  if (input && typeof input.url === 'string') return input.url;
  return '';
}

function isApiRequest(url) {
  if (!url) return false;
  if (url.startsWith('/api/')) return true;
  try {
    return new URL(url, window.location.origin).origin === window.location.origin
      && new URL(url, window.location.origin).pathname.startsWith('/api/');
  } catch {
    return false;
  }
}

export function installFetchInterceptor() {
  if (typeof window === 'undefined' || window[PATCH_FLAG]) return;
  const originalFetch = window.fetch.bind(window);

  window.fetch = (input, init = {}) => {
    let nextInit = init;
    try {
      if (isApiRequest(resolveUrl(input))) {
        const headers = new Headers(init.headers || undefined);
        const token = getToken();
        if (token && !headers.has('Authorization')) {
          headers.set('Authorization', `Bearer ${token}`);
        }
        Object.entries(getAiHeaders()).forEach(([name, value]) => {
          if (value && !headers.has(name)) headers.set(name, value);
        });
        nextInit = { ...init, headers };
      }
    } catch {
      // 拦截器出错时退回原始请求，不阻断业务
      nextInit = init;
    }
    return originalFetch(input, nextInit);
  };

  window[PATCH_FLAG] = true;
}

export default installFetchInterceptor;
