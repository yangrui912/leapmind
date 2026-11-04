// import { StrictMode } from 'react'
// import { createRoot } from 'react-dom/client'
// import './index.css'
// import App from './App.jsx'

// createRoot(document.getElementById('root')).render(
//   <StrictMode>
//     <App />
//   </StrictMode>,
// )

import React from 'react';
import ReactDOM from 'react-dom/client';
import './App.css';
import App from './App';
import { installFetchInterceptor } from './services/fetchInterceptor';

// 统一为 /api/ 请求补齐登录 token 与用户自填的 AI 配置头（含直接使用 fetch 的模块）
installFetchInterceptor();

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
