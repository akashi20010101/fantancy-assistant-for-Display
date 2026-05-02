
import { callGemini } from "../core/gemini";
import { callGLM } from "../core/glm";
import { Bindings } from "../types";

/**
 * 🌟 多模型响应解析配置
 * 未来增加新模型只需在此处添加解析逻辑
 */
const chunkParsers: Record<string, (json: any) => string> = {
    "glm": (json) => json.choices?.[0]?.delta?.content || "",
    "gemini": (json) => json.candidates?.[0]?.content?.parts?.[0]?.text || "",
    // "deepseek": (json) => json.choices?.[0]?.delta?.content || "",
};

/**
 * 统一流式解析器
 * 适配 Gemini 和 豆包(OpenAI格式)
 */
// 解析流式分片 + 输出流原始日志
export function parseStreamChunk(provider: string, line: string): string {
    const part = line.trim();
    if (!part || !part.startsWith("data:")) return "";
    
    try {
        const cleaned = part.replace(/^data: /, "");
        if (cleaned === "[DONE]") return "";
        
        const json = JSON.parse(cleaned);
        // 使用映射表进行解析，找不到则尝试默认逻辑
        const parser = chunkParsers[provider] || ((j: any) => j.choices?.[0]?.delta?.content || "");
        return parser(json);
    } catch (e) {
        console.warn("[Util] 解析单条流失败，已跳过:", line);
        return "";
    }
}


// ==============================
// 分发 AI 调用（兼容双厂商）
// ==============================
export async function dispatchChat(provider: string, env: any, messages: any[], options: any, maxRetries = 10): Promise<Response> {
    let retryCount = 0;
    const MAX_RETRIES = maxRetries;
    let delayMs = 2000;

    console.log(`[Dispatch] 开始调度 AI 厂商: ${provider}`);

    while (retryCount < MAX_RETRIES) {
        try {
            const response = provider === "glm" 
                ? await callGLM(env.GLM_API_KEY, messages, options)
                : await callGemini(env.GOOGLE_GENERATIVE_AI_API_KEY, messages, options);

            // ✨ 情况 A：成功，直接带走！
            if (response.ok) {
                console.log(`[Dispatch] ${provider} 调用成功`);
                return response;
            }

            // ✨ 情况 B：503 忙碌，准许进入重试轮次
            if (response.status === 503) {
                console.log(`[Dispatch] 服务器 503 忙碌，准备进行第 ${retryCount + 1} 次重试...`);
                // 这里不 return，让它跑完循环底部的等待逻辑
            } 
            else {
                // ✨ 情况 C：401, 400, 404 等，重试一百次也没用，直接原地“爆炸”
                console.log(`[Dispatch] 遭遇不可恢复错误: ${response.status}，停止重试`);
                return response; // 或者 throw new Error(`Status ${response.status}`);
            }

        } catch (e) {
            // 如果是网络层面的丢包（非 status 错误），也可以视情况重试，或者直接抛出
            console.log(`[Dispatch] 尝试过程中捕获异常:`, e);
            throw e; 
        }

        // 💡 只有 503 会走到这里的等待逻辑
        retryCount++;
        if (retryCount < MAX_RETRIES) {
            console.log(`[Dispatch] 等待 ${delayMs / 1000} 秒后进行第 ${retryCount + 1} 次重试...`);
            await new Promise(resolve => setTimeout(resolve, delayMs));
            
            // 你的 5秒 封顶策略
            delayMs = Math.min(delayMs * 2, 5000);
        }
    }

    throw new Error(`[Dispatch] ${provider} 在重试 ${MAX_RETRIES} 次后依然返回 503。`);
}

// 辅助函数：基于用户地域转换相对时间
export function formatRelativeTime(dbTimeStr: string, nowTs: number, userTimezone: string): string {
    console.log("[Util] 格式化相对时间 | 数据库时间:", dbTimeStr, "| 时区:", userTimezone);
    
    const msgDate = new Date(dbTimeStr + (dbTimeStr.endsWith('Z') ? "" : "Z"));
    const msgTs = msgDate.getTime();
    const diffMin = Math.floor((nowTs - msgTs) / 60000);

    if (diffMin < 1) return "刚刚";
    if (diffMin < 60) return `${diffMin}分钟前`;
    
    try {
        return new Intl.DateTimeFormat('zh-CN', {
            hour: '2-digit', minute: '2-digit',
            timeZone: userTimezone,
            hour12: false
        }).format(msgDate);
    } catch (e) {
        console.error("[Util] 时间格式化失败:", e);
        return `${Math.floor(diffMin / 6)}小时前`;
    }
}