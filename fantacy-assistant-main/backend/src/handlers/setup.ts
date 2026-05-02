import { Hono } from "hono";
import { Bindings } from "../types/bindings";
import { getUserId } from "../utils/auth";

const setup = new Hono<{ Bindings: Bindings }>();

/**
 * 1. 提交/更新用户及助手资料
 */
setup.post("/upsertProfiles", async (c) => {
    console.log("=== [setup] upsertProfiles 开始 ===");

    const userId = await getUserId(c);
    console.log("用户ID:", userId);

    const b = await c.req.json();
    console.log("前端传入参数:", JSON.stringify(b, null, 2));
    
    const ts = new Date().toLocaleString('zh-CN');
    console.log("系统时间戳:", ts);

    // 助手诞生日期（系统当前日期）
    const assistant_birthday = new Date().toISOString().split('T')[0];
    console.log("助手诞生日期:", assistant_birthday);

    // ====================== 用户资料 SQL（全 user_ 前缀） ======================
    const userSql = `
        INSERT INTO user_profiles (
            user_id,
            user_nickname,
            user_gender,
            user_birthday,
            user_occupation,
            user_mbti,
            user_hobbies,
            user_communication_style,
            user_goals,
            user_taboos,
            user_bio,
            user_avatar_url,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(user_id) DO UPDATE SET
            user_nickname=excluded.user_nickname,
            user_gender=excluded.user_gender,
            user_birthday=excluded.user_birthday,
            user_occupation=excluded.user_occupation,
            user_mbti=excluded.user_mbti,
            user_hobbies=excluded.user_hobbies,
            user_communication_style=excluded.user_communication_style,
            user_goals=excluded.user_goals,
            user_taboos=excluded.user_taboos,
            user_bio=excluded.user_bio,
            user_avatar_url=excluded.user_avatar_url,
            updated_at=excluded.updated_at
    `;

    const userParams = [
        userId,
        b.user_nickname ?? '',
        b.user_gender ?? '',
        b.user_birthday ?? '',
        b.user_occupation ?? '',
        b.user_mbti ?? '',
        b.user_hobbies ?? '',
        b.user_communication_style ?? '',
        b.user_goals ?? '',
        b.user_taboos ?? '',
        b.user_bio ?? '',
        '', // 头像上传后再赋值，默认空
        ts
    ];

    // ====================== 助手资料 SQL（全 assistant_ 前缀） ======================
    const assistantSql = `
        INSERT INTO assistant_profiles (
            user_id,
            assistant_name,
            assistant_gender,
            assistant_birthday,
            assistant_role_identity,
            assistant_personality_tags,
            assistant_speaking_style,
            assistant_expertise,
            assistant_values_system,
            assistant_background_story,
            assistant_avatar_url,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(user_id) DO UPDATE SET
            assistant_name=excluded.assistant_name,
            assistant_gender=excluded.assistant_gender,
            assistant_birthday=excluded.assistant_birthday,
            assistant_role_identity=excluded.assistant_role_identity,
            assistant_personality_tags=excluded.assistant_personality_tags,
            assistant_speaking_style=excluded.assistant_speaking_style,
            assistant_expertise=excluded.assistant_expertise,
            assistant_values_system=excluded.assistant_values_system,
            assistant_background_story=excluded.assistant_background_story,
            assistant_avatar_url=excluded.assistant_avatar_url,
            updated_at=excluded.updated_at
    `;

    const assistantParams = [
        userId,
        b.assistant_name ?? '',
        b.assistant_gender ?? '',
        assistant_birthday,
        b.assistant_role_identity ?? '',
        b.assistant_personality_tags ?? '',
        b.assistant_speaking_style ?? '',
        b.assistant_expertise ?? '',
        b.assistant_values_system ?? '',
        b.assistant_background_story ?? '',
        '', // 头像上传后再赋值，默认空
        ts
    ];

    // 打印日志
    console.log("=====================================");
    console.log("用户SQL参数：", userParams);
    console.log("助手SQL参数：", assistantParams);
    console.log("=====================================");

    try {
        await c.env.fantacy_db.batch([
            c.env.fantacy_db.prepare(userSql).bind(...userParams),
            c.env.fantacy_db.prepare(assistantSql).bind(...assistantParams)
        ]);

        console.log("数据库执行成功");
        return c.json({ success: true, message: "设置保存成功" });
    } catch (e: any) {
        console.error("执行异常:", e);
        return c.json({ success: false, error: e.message }, 500);
    }
});

/**
 * 2. 获取用户状态与资料 (POST方式，适配安卓端)
 */
setup.post("/fetchStatus", async (c) => {
    console.log("=== [setup] fetchStatus 开始 ===");

    const userId = await getUserId(c);
    console.log("用户ID:", userId);

    try {
        console.log("开始查询用户+助手资料...");

        const profile = await c.env.fantacy_db.prepare(`
            SELECT u.nickname, a.name as assistantName 
            FROM user_profiles u 
            LEFT JOIN assistant_profiles a ON u.user_id = a.user_id 
            WHERE u.user_id = ?
        `).bind(userId).first();

        console.log("查询结果:", profile);

        const needsSetup = !profile || !profile.nickname || !profile.assistantName;
        console.log("是否需要引导 setup:", needsSetup);

        console.log("=== [setup] fetchStatus 结束 ===");

        return c.json({
            success: true,
            needsSetup: needsSetup,
            data: profile || null
        });

    } catch (error) {
        console.error("fetchStatus 查询异常:", error);
        return c.json({ success: false, error: "Database error" }, 500);
    }
});

export default setup;