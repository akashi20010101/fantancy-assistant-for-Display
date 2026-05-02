import { ChatMessage } from "../types";

export async function callGemini(
    apiKey: string, 
    messages: ChatMessage[], 
    options: { stream?: boolean; model?: string } = {}
) {
    // 🔥 固定使用你确认可用的模型 + v1beta
    const MODEL = options.model || "gemini-2.5-flash";
    const API_VERSION = "v1beta";
    const endpoint = options.stream ? "streamGenerateContent" : "generateContent";

    // 构建 URL（自动处理 key / alt=sse）
    const url = new URL(
        `https://generativelanguage.googleapis.com/${API_VERSION}/models/${MODEL}:${endpoint}`
    );
    url.searchParams.append("key", apiKey);
    if (options.stream) {
        url.searchParams.append("alt", "sse"); // 流式必须加
    }

    // 提取 system prompt
    const systemInstruction = messages.find(m => m.role === 'system')?.content;

    // 转换消息格式（Gemini 要求 role: user / model）
    const contents = messages
        .filter(m => m.role !== 'system')
        .map(m => ({
            role: m.role === 'assistant' ? 'model' : 'user',
            parts: [{ text: m.content }]
        }));

    // 发起请求
    const response = await fetch(url.toString(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            contents,
            system_instruction: systemInstruction 
                ? { parts: [{ text: systemInstruction }] } 
                : undefined,
                // ✨ 追加所有常用内置工具
            tools: [
                // 1. Google 搜索工具（查地图、查实时新闻、查店铺）
                {
                    "google_search": {}
                }
            ],
            generationConfig: {
                temperature: 0.7,
                topP: 0.95
            }
        })
    });

    // 错误处理
    if (!response.ok) {
        const errorJson = await response.json().catch(() => ({}));
        throw new Error(`Gemini API ${response.status}: ${JSON.stringify(errorJson)}`);
    }

    return response;
}