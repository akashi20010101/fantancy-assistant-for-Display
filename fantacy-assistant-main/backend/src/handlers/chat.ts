import { Hono } from "hono";
import { streamText } from "hono/streaming";
import { verify } from 'hono/jwt';
import { Bindings } from "../types";
import { callGemini } from "../core/gemini";
import { callGLM } from "../core/glm";
import { upsertMessageVector } from "../core/vector";
import * as Prompts from "../core/prompts";
import { extractJSON } from "../utils/formatter";
import { parseStreamChunk } from "../utils/streamParser";
import { dispatchChat } from "../utils/streamParser";

const chat = new Hono<{ Bindings: Bindings }>();

// 延迟（用于重试）
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

// ==============================
// 删除单轮对话
// ==============================
chat.post("/delete", async (c) => {
    console.log("\n=====================================");
    console.log("[Delete] 删除消息接口开始执行");

    const authHeader = c.req.header('Authorization');
    console.log("[Delete] 获取到 Authorization 请求头:", authHeader);

    if (!authHeader) {
        console.log("[Delete] 无 Token，拒绝访问，返回 401 Unauthorized");
        return c.json({ error: "Unauthorized" }, 401);
    }

    const token = authHeader.replace("Bearer ", "");
    console.log("[Delete] 提取 Bearer Token 成功:", token);

    try {
        console.log("[Delete] 开始使用 JWT 密钥验证 Token");
        const payload = await verify(token, c.env.JWT_SECRET, "HS256");
        const userId = payload.id as number;
        console.log("[Delete] Token 验证成功，当前用户 ID:", userId);

        const { createdAt } = await c.req.json();
        console.log("[Delete] 前端传入需要删除的消息创建时间戳:", createdAt);

        console.log("[Delete] 开始执行数据库软删除操作");
        await c.env.fantacy_db.prepare(
            "UPDATE chat_history SET is_deleted = 1 WHERE user_id = ? AND (created_at = ? OR created_at = ?)"
        ).bind(userId, new Date(createdAt).toISOString(), new Date(createdAt + 1).toISOString()).run();

        console.log("[Delete] 数据库删除执行完成，消息已标记为已删除");
        console.log("[Delete] 删除接口执行成功 ✅");
        console.log("=====================================\n");
        return c.json({ success: true });
    } catch (err) {
        console.error("[Delete] 删除接口发生异常，错误信息:", err);
        return c.json({ error: "Delete Failed" }, 500);
    }
});

// ==============================
// 获取聊天历史
// ==============================
chat.get("/history", async (c) => {
    console.log("\n=====================================");
    console.log("[History] 获取聊天记录接口开始执行");

    const authHeader = c.req.header('Authorization');
    console.log("[History] 获取到 Authorization 请求头:", authHeader);

    const userTimezone = c.req.header('X-Timezone') || 'Asia/Tokyo';
    console.log("[History] 获取用户时区:", userTimezone);

    if (!authHeader) {
        console.log("[History] 无 Token，拒绝访问，返回 401 Unauthorized");
        return c.json({ error: "Unauthorized" }, 401);
    }

    const token = authHeader.replace("Bearer ", "");
    console.log("[History] 提取 Bearer Token 成功:", token);

    const beforeTs = parseInt(c.req.query('before') || Date.now().toString());
    console.log("[History] 获取翻页时间戳 before:", beforeTs);

    const beforeIso = new Date(beforeTs).toISOString();
    console.log("[History] 翻页时间戳转换为 ISO 时间:", beforeIso);

    try {
        console.log("[History] 开始使用 JWT 密钥验证 Token");
        const payload = await verify(token, c.env.JWT_SECRET, "HS256");
        const userId = payload.id as number;
        console.log("[History] Token 验证成功，当前用户 ID:", userId);

        console.log("[History] 开始查询数据库，获取未删除的聊天记录");
        const history = await c.env.fantacy_db.prepare(
            "SELECT id, role, content, created_at FROM chat_history WHERE user_id = ? AND created_at < ? AND is_deleted = 0 ORDER BY created_at DESC LIMIT 20"
        ).bind(userId, beforeIso).all();
        console.log("[History] 数据库查询完成，原始结果条数:", history.results?.length || 0);

        console.log("[History] 开始格式化聊天记录");
        const formattedData = history.results.map((row: any) => ({
            id: row.id,
            role: row.role,
            content: row.content,
            created_at: new Date(row.created_at + (row.created_at.endsWith('Z') ? "" : "Z")).getTime()
        })).reverse();
        console.log("[History] 格式化完成，最终返回记录数:", formattedData.length);

        console.log("[History] 聊天记录获取接口执行成功 ✅");
        console.log("=====================================\n");
        return c.json({ success: true, data: formattedData });
    } catch (err) {
        console.error("[History] 获取聊天记录接口异常:", err);
        return c.json({ error: "Invalid Token" }, 401);
    }
});

// ==============================
// 发送消息 & 流式回复
// ==============================
chat.post("/", async (c) => {
    console.log("\n=====================================");
    console.log("[Chat] 聊天接口开始处理新消息");

    const authHeader = c.req.header('Authorization');
    console.log("[Chat] 获取 Authorization 请求头:", authHeader);

    const userTimezone = c.req.header('X-Timezone') || 'Asia/Tokyo';
    console.log("[Chat] 用户时区:", userTimezone);

    if (!authHeader) {
        console.log("[Chat] 无 Token，返回 401 Unauthorized");
        return c.json({ error: "Unauthorized" }, 401);
    }

    const token = authHeader.replace("Bearer ", "");
    let userId: number;

    try {
        console.log("[Chat] 开始验证 JWT Token");
        const payload = await verify(token, c.env.JWT_SECRET, "HS256");
        userId = payload.id as number;
        console.log("[Chat] Token 验证成功，用户 ID:", userId);
    } catch (err) {
        console.error("[Chat] Token 验证失败，错误:", err);
        return c.json({ error: "Invalid Token" }, 401);
    }

    console.log("[Chat] 开始解析请求体 JSON");
    const { message, provider = "gemini" } = await c.req.json();
    console.log("[Chat] 用户输入消息:", message);
    console.log("[Chat] 使用 AI 厂商:", provider);

    const ts = Date.now();
    const oneDayAgo = new Date(ts - 24 * 60 * 60 * 1000).toISOString();
    console.log("[Chat] 当前时间戳:", ts);
    console.log("[Chat] 24 小时前 ISO 时间:", oneDayAgo);

    const userLocalNow = new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
        timeZone: userTimezone, hour12: false
    }).format(new Date(ts));
    console.log("[Chat] 用户本地时间（格式化后）:", userLocalNow);

    try {
        console.log("[Chat] 开始并行加载：用户信息、人设、历史、向量等数据");
        const [userRecord, uBase, aBase, uAdd, aAdd, recentHistory, queryVectorResp, totalCount] = await Promise.all([
            c.env.fantacy_db.prepare("SELECT status FROM user WHERE id = ?").bind(userId).first<{ status: number }>(),
            c.env.fantacy_db.prepare("SELECT * FROM user_profiles WHERE user_id = ?").bind(userId).first<any>(),
            c.env.fantacy_db.prepare("SELECT * FROM assistant_profiles WHERE user_id = ?").bind(userId).first<any>(),
            c.env.fantacy_db.prepare("SELECT content FROM user_add_profiles WHERE user_id = ?").bind(userId).first<any>(),
            c.env.fantacy_db.prepare("SELECT content FROM assistant_add_profiles WHERE user_id = ?").bind(userId).first<any>(),
            c.env.fantacy_db.prepare("SELECT role, content FROM chat_history WHERE user_id = ? AND created_at > ? AND is_deleted = 0 ORDER BY created_at DESC LIMIT 20").bind(userId, oneDayAgo).all(),
            c.env.AI.run("@cf/baai/bge-small-en-v1.5", { text: [message] }),
            c.env.fantacy_db.prepare("SELECT COUNT(*) as count FROM chat_history WHERE user_id = ? AND is_deleted = 0").bind(userId).first<{ count: number }>()
        ]);

        console.log("[Chat] 用户状态查询结果:", userRecord);
        console.log("[Chat] 24 小时内历史消息条数:", recentHistory.results.length);
        console.log("[Chat] 用户累计消息总数:", totalCount?.count || 0);

        if (!userRecord || userRecord.status === 1) {
            console.log("[Chat] 用户不存在或已被停用，返回 403");
            return c.json({ error: "Deactivated" }, 403);
        }

        console.log("[Chat] 对最近聊天记录进行倒序排序");
        const sortedHistory = recentHistory.results.reverse();

        console.log("[Chat] 拼接对话上下文");
        const recentContext = sortedHistory.length > 0
            ? sortedHistory.map((r: any) => `[${r.role === 'user' ? '用户' : '你'}]: ${r.content}`).join("\n")
            : "暂无记录";
        console.log("[Chat] 对话上下文拼接完成，长度:", recentContext.length);

        console.log("[Chat] 开始拼接用户完整人设");
        const uBio = Prompts.buildFullUserBio(uBase, uAdd?.content);
        console.log("[Chat] 开始拼接助手完整人设");
        const aBio = Prompts.buildFullAssistantBio(aBase, aAdd?.content);
        console.log("[Chat] 用户/助手人设拼接完成");

        console.log("[Chat] 开始执行向量检索，匹配用户记忆");
        const vectorMatches = await c.env.VECTOR_INDEX.query(queryVectorResp.data[0], { topK: 3, returnMetadata: true, filter: { user_id: userId } });
        const memories = vectorMatches.matches.map(m => `- ${m.metadata?.text ?? ""}`).join("\n");
        console.log("[Chat] 向量检索完成，匹配记忆条数:", vectorMatches.matches.length);

        console.log("[Chat] 开始构建最终 System Prompt");
        const systemPrompt = Prompts.buildSystemPrompt(aBase?.name ?? "助手", aBio, aBase?.personality_tags ?? '自然', uBase?.nickname ?? "主人", uBio, userLocalNow, recentContext, memories);
        console.log("[Chat] System Prompt 构建完成，长度:", systemPrompt.length);

        let aiResponse: Response | null = null;
        const messages = [{ role: 'system', content: systemPrompt }, { role: 'user', content: message }];
        console.log("[Chat] 准备发送给 AI 的消息结构:", JSON.stringify(messages));

        try {
            console.log("[Chat] 开始调用 dispatchChat 发起 AI 请求（内部自带重试）");
            aiResponse = await dispatchChat(provider, c.env, messages, { stream: true });
            console.log("[Chat] dispatchChat 调用完成，响应状态:", aiResponse?.status);
        } catch (err) {
            console.error("[Chat] AI 服务重试后仍然失败:", err);
            return c.json({ error: "AI Service Error after retries" }, 503);
        }

        if (!aiResponse || !aiResponse.ok) {
            console.error("[Chat] AI 响应无效或状态码异常，状态:", aiResponse?.status);
            return c.json({ error: "AI Service Unavailable" }, 503);
        }

        console.log("[Chat] AI 连接正常，开始建立 SSE 流式响应");
        return streamText(c, async (stream) => {
            let fullReply = "";
            const reader = aiResponse!.body?.getReader();
            const decoder = new TextDecoder();
            let buffer = "";

            if (!reader) {
                console.error("[Chat] AI 流读取器为空，无法继续");
                return;
            }

            try {
                while (true) {
                    const { done, value } = await reader.read();

                    if (done) {
                        console.log("[Chat] AI 流式传输结束，完整回复内容:", fullReply);
                        await stream.write("data: [DONE]\n\n");

                        const currentTotal = (totalCount?.count || 0) + 2;
                        const shouldTriggerEvolve = currentTotal > 0 && currentTotal % 20 === 0;
                        console.log("[Chat] 对话完成，当前总消息数:", currentTotal);
                        console.log("[Chat] 是否触发进化逻辑:", shouldTriggerEvolve);

                        console.log("[Chat] 异步启动 evolve 进化流程");
                        c.executionCtx.waitUntil(
                            evolve(c, userId, message, fullReply, ts, uAdd?.content || "", aAdd?.content || "", shouldTriggerEvolve, recentContext, provider)
                        );
                        break;
                    }

                    buffer += decoder.decode(value, { stream: true });
                    let lines = buffer.split('\n');
                    buffer = lines.pop() || "";

                    for (const line of lines) {
                        const text = parseStreamChunk(provider, line);
                        if (text) {
                            fullReply += text;
                            await stream.write(`data: ${JSON.stringify({ choices: [{ delta: { content: text } }] })}\n\n`);
                        }
                    }
                }
            } finally {
                console.log("[Chat] 释放流读取器锁");
                reader.releaseLock();
            }
        });

    } catch (err) {
        console.error("[Chat] 聊天接口全局捕获异常:", err);
        return c.json({ error: String(err) }, 500);
    }
});

// ==============================
// 进化逻辑
// ==============================
async function evolve(
    c: any, 
    userId: number, 
    message: string, 
    fullReply: string, 
    ts: number, 
    oldU: string, 
    oldA: string, 
    shouldTriggerEvolve: boolean,
    sessionContext: string, 
    provider: string
) {
    try {
        console.log("\n=====================================");
        console.log("[Evolve] 进化流程开始执行");
        console.log("[Evolve] 当前用户 ID:", userId);
        console.log("[Evolve] 是否触发进化:", shouldTriggerEvolve);

        const userTime = new Date(ts).toISOString();
        const assistantTime = new Date(ts + 1).toISOString();
        console.log("[Evolve] 用户消息入库时间:", userTime);
        console.log("[Evolve] 助手回复入库时间:", assistantTime);

        // --- 1. 持久化对话 ---
        console.log("[Evolve] 开始将用户消息和助手回复插入数据库");
        await c.env.fantacy_db.prepare(
            "INSERT INTO chat_history (user_id, role, content, created_at) VALUES (?, 'user', ?, ?), (?, 'assistant', ?, ?)"
        ).bind(userId, message, userTime, userId, fullReply, assistantTime).run();
        console.log("[Evolve] 聊天记录入库完成");

        // --- 2. 向量化 ---
        console.log("[Evolve] 开始对对话内容进行向量存储");
        await Promise.all([
            upsertMessageVector(c, userId, message, 'user', String(ts)),
            upsertMessageVector(c, userId, fullReply, 'assistant', String(ts + 1))
        ]);
        console.log("[Evolve] 向量存储完成");

        // --- 3. 进化 ---
        if (shouldTriggerEvolve) {
            console.log("[Evolve] 进入进化总结流程");
            
            const currentSession = `${sessionContext}\n[用户]: ${message}\n[助手]: ${fullReply}`;
            console.log("[Evolve] 本次进化使用的对话上下文长度:", currentSession.length);

            console.log("[Evolve] 构建进化提示词");
            const evolutionPrompt = Prompts.buildEvolutionPrompt(oldU, oldA, currentSession);
            console.log("[Evolve] 进化提示词构建完成");

            try {
                const maxRetries = 10;
                console.log("[Evolve] 调用 AI 生成进化总结，非流式");
                const extResp = await dispatchChat(provider, c.env, [{ role: 'user', content: evolutionPrompt }], { stream: false },maxRetries);
                console.log("[Evolve] AI 进化调用响应状态:", extResp.status);

                console.log("[Evolve] 解析 AI 返回的 JSON 结果");
                const rawResponse = (await extResp.json()) as any;
                console.log("[Evolve] 原始响应:", JSON.stringify(rawResponse));

                let contentText = "";
                if (provider === "gemini") {
                    contentText = rawResponse.candidates?.[0]?.content?.parts?.[0]?.text || "";
                } else {
                    contentText = rawResponse.choices?.[0]?.message?.content || "";
                }
                console.log("[Evolve] 提取 AI 回复文本:", contentText);

                console.log("[Evolve] 尝试从文本中提取 JSON");
                const p = extractJSON(contentText);
                if (p) {
                    console.log("[Evolve] JSON 提取成功:", JSON.stringify(p));

                    const dbTime = new Date(ts + 2).toISOString();
                    console.log("[Evolve] 数据库更新时间:", dbTime);

                    const userContent = (p.user_evolution && typeof p.user_evolution === 'object') 
                        ? JSON.stringify(p.user_evolution) 
                        : (p.user_evolution || "");
                        
                    const botContent = (p.bot_evolution && typeof p.bot_evolution === 'object') 
                        ? JSON.stringify(p.bot_evolution) 
                        : (p.bot_evolution || "");

                    console.log("[Evolve] 准备更新用户增量人设:", userContent);
                    console.log("[Evolve] 准备更新助手增量人设:", botContent);

                    await c.env.fantacy_db.batch([
                        c.env.fantacy_db.prepare("INSERT INTO user_add_profiles (user_id, content, updated_at) VALUES (?, ?, ?) ON CONFLICT(user_id) DO UPDATE SET content=excluded.content, updated_at=excluded.updated_at").bind(userId, userContent, dbTime),
                        c.env.fantacy_db.prepare("INSERT INTO assistant_add_profiles (user_id, content, updated_at) VALUES (?, ?, ?) ON CONFLICT(user_id) DO UPDATE SET content=excluded.content, updated_at=excluded.updated_at").bind(userId, botContent, dbTime)
                    ]);
                    
                    console.log("[Evolve] 进化总结保存数据库成功 ✅");
                } else {
                    console.error("[Evolve] 无法提取有效 JSON，进化失败");
                }
            } catch (chatError) {
                console.error("[Evolve] 进化流程 AI 调用异常:", chatError);
            }
        } else {
            console.log("[Evolve] 不满足进化条件，跳过总结");
        }
        
        console.log("[Evolve] 进化流程全部执行完成 ✅");
        console.log("=====================================\n");
    } catch (e) {
        console.error("[Evolve] 进化流程全局异常:", e);
    }
}

export default chat;