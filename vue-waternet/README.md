# 数字孪生水网前端

Vue + TypeScript + Vite 前端，用于数字孪生水网平台的指挥舱、地图底板、仿真预演和预警展示。

## 本地启动

```sh
npm install
npm run dev
```

默认访问地址：

```text
http://127.0.0.1:5173/
```

## 环境变量

复制 `.env.example` 为 `.env.local`，按需修改：

```text
VITE_API_BASE=http://localhost:8080/api
VITE_TIANDITU_TOKEN=your-tianditu-token
```

`VITE_TIANDITU_TOKEN` 是天地图 Web API 密钥。未配置时，2D 地图区域会显示配置提示；配置后会加载真实天地图底图，并叠加河网、节点和节点弹窗。

## 常用命令

```sh
npm run build
npm run test:unit -- --run
```
