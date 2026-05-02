import { Hono } from "hono";
import { cors } from 'hono/cors';
import { Bindings } from "./types/bindings";
import auth from "./handlers/auth";
import setup from "./handlers/setup";
import chat from "./handlers/chat"; // 稍后把你原本的 AI 逻辑拆到这里

const app = new Hono<{ Bindings: Bindings }>();

app.use('*', cors());

// 路由挂载：实现功能分区
app.route("/auth", auth); // 对应 Android 端访问 /auth/login 或 /auth/register
app.route("/chat", chat); // 对应聊天功能
app.route("/setup", setup); // 对应聊天功能

export default app;