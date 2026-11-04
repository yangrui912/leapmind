import { useEffect, useState } from 'react';
import { loadAiSettings, saveAiSettings, clearAiSettings } from '../../services/aiSettings';

/**
 * AI 服务配置弹窗
 *
 * 用户自行填写 OpenAI 兼容接口的地址、访问令牌与模型名；
 * 配置只保存在本机浏览器，随 AI 请求透传给后端，服务端不保存。
 */
export default function AiSettingsDialog({ open, onClose }) {
  const [baseUrl, setBaseUrl] = useState('');
  const [token, setToken] = useState('');
  const [model, setModel] = useState('');
  const [sessionOnly, setSessionOnly] = useState(false);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (!open) return;
    const current = loadAiSettings();
    setBaseUrl(current?.baseUrl || '');
    setToken(current?.token || '');
    setModel(current?.model || '');
    setSessionOnly(Boolean(current?.sessionOnly));
    setNotice('');
  }, [open]);

  if (!open) return null;

  const handleSave = () => {
    if (!baseUrl.trim() || !token.trim()) {
      setNotice('请填写服务地址和访问令牌');
      return;
    }
    saveAiSettings({ baseUrl, token, model }, { sessionOnly });
    setNotice('已保存，本次配置只在本机生效');
    setTimeout(() => {
      onClose?.();
    }, 600);
  };

  const handleClear = () => {
    clearAiSettings();
    setBaseUrl('');
    setToken('');
    setModel('');
    setNotice('已清除本机保存的 AI 配置');
  };

  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center bg-slate-900/50 p-4">
      <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl">
        <div className="mb-1 flex items-start justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">AI 服务配置</h2>
            <p className="mt-1 text-sm text-slate-500">
              使用你自己的模型服务，配置只保存在本机浏览器，不会上传到服务器保存。
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="关闭"
            className="rounded-lg px-2 py-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
          >
            ✕
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">服务地址</span>
            <input
              type="text"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              placeholder="https://your-host/v1"
              autoComplete="off"
              spellCheck={false}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
            />
            <span className="mt-1 block text-xs text-slate-400">
              OpenAI 兼容接口的 base url（通常以 /v1 结尾，不要带 /chat/completions）
            </span>
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">访问令牌</span>
            <input
              type="password"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="sk-..."
              autoComplete="off"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
            />
            <span className="mt-1 block text-xs text-slate-400">
              仅随请求发送给后端并转发到你的模型服务，不写入日志与数据库
            </span>
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">模型名称（可选）</span>
            <input
              type="text"
              value={model}
              onChange={(e) => setModel(e.target.value)}
              placeholder="例如 qwen2.5-7b-instruct"
              autoComplete="off"
              spellCheck={false}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
            />
          </label>

          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input
              type="checkbox"
              checked={sessionOnly}
              onChange={(e) => setSessionOnly(e.target.checked)}
              className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
            />
            仅本次会话（关闭浏览器后需要重新填写）
          </label>
        </div>

        {notice && (
          <p className="mt-4 rounded-lg bg-indigo-50 px-3 py-2 text-sm text-indigo-700">{notice}</p>
        )}

        <div className="mt-6 flex items-center justify-between">
          <button
            type="button"
            onClick={handleClear}
            className="rounded-lg px-3 py-2 text-sm text-slate-500 transition hover:bg-slate-100 hover:text-slate-700"
          >
            清除配置
          </button>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg px-4 py-2 text-sm text-slate-600 transition hover:bg-slate-100"
            >
              取消
            </button>
            <button
              type="button"
              onClick={handleSave}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-700"
            >
              保存
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
