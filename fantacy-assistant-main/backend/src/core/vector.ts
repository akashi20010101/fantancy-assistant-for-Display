// src/core/vector.ts
import { Bindings } from "../types";

export async function upsertMessageVector(
  c: { env: Bindings },
  userId: number,
  text: string,
  role: 'user' | 'assistant',
  ts: string
) {
  // 长度过滤：太短的句子（如“嗯”、“好的”）不具备检索价值，跳过以节省空间
  if (text.length < 2) return;

  try {
    const embed = await c.env.AI.run('@cf/baai/bge-small-en-v1.5', { text: [text] });
    
    // 生成唯一 ID：加入 userId 前缀防止冲突，方便以后可能的批量管理
    const vectorId = `msg_${userId}_${Date.now()}_${Math.random().toString(36).slice(2, 5)}`;

    await c.env.VECTOR_INDEX.upsert([{ 
      id: vectorId, 
      values: embed.data[0], 
      metadata: { 
        text: text,      // 必须存原始文本，Gemini 读这个
        user_id: userId, // 极其重要：用于检索时的隔离过滤
        role: role,      // 标记是谁说的
        time: ts         // 标记时间点
      } 
    }]);
  } catch (e) {
    console.error("Vector Upsert Error:", e);
  }
}