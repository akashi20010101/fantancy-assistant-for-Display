// core/glm.ts

export async function callGLM(
    apiKey: string,
    messages: { role: string; content: string }[],
    options: { stream?: boolean } = { stream: true }
) {
    const url = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    const MODEL_NAME = "glm-4-flash"; 

    const response = await fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${apiKey}`
        },
        body: JSON.stringify({
            model: MODEL_NAME, 
            messages: messages,
            stream: options.stream ?? true,
            tools: [
                {
                    type: "web_search",
                    // ✨ 修正1：必须显式设置 enable: true 开启联网
                    web_search: {
                        enable: true,
                        search_result: true // 建议开启，可在回复中引用搜索结果来源
                    }
                }
            ],
            // ✨ 修正2：移除错误的 tool_choice 配置，或设置为 "auto" 让模型自主判断
            tool_choice: "auto",
            do_sample: true, 
            temperature: 0.85, 
            top_p: 0.95,
            presence_penalty: 0.2
        })
    });

    // 错误拦截逻辑
    if (!response.ok) {
        const errorDetail = await response.json().catch(() => ({ message: "未知错误" })) as any;
        throw new Error(`GLM 调用异常 (${response.status}): ${errorDetail.error?.message || JSON.stringify(errorDetail)}`);
    }

    return response;
}