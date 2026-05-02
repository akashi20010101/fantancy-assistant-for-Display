import { Hono } from "hono";
import { sign } from 'hono/jwt';
import { Bindings } from "../types/bindings";
import { SMTPClient } from 'emailjs';

const auth = new Hono<{ Bindings: Bindings }>();

/**
 * 🌟 核心加密函数：使用 SHA-256 对密码进行哈希处理
 */
async function hashPassword(password: string, env: any): Promise<string> {
  // 建议在 wrangler.toml 的 [vars] 中配置 PASSWORD_SALT，若无则使用默认值
  const salt = env.PASSWORD_SALT || "fantacy_default_salt_2026"; 
  const msgUint8 = new TextEncoder().encode(password + salt);
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgUint8);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * 🌟 使用 Gmail 发送验证码
 */
async function sendGmail(to: string, code: string, env: any) {
  const client = new SMTPClient({
    user: env.MY_EMAIL,
    password: env.EMAIL_API_KEY,
    host: 'smtp.gmail.com',
    ssl: true,
  });

  try {
    await client.sendAsync({
      text: `您的验证码是：${code}，请在10分钟内使用。`,
      from: `Fantacy Assistant <${env.MY_EMAIL}>`,
      to: to,
      subject: '【Fantacy】验证码',
    });
    console.log(`邮件已成功发送至: ${to}`);
    return true;
  } catch (err: any) {
    console.error('Gmail直连报错:', err);
    return false;
  }
}

// --- 1. 发送验证码接口 (使用 Upsert 逻辑) ---
auth.post("/send-code", async (c) => {
  const body = await c.req.json();
  console.log("收到的请求体:", JSON.stringify(body));
  
  const { email } = body;
  if (!email) {
    console.error("错误: 请求中缺失 email 字段");
    return c.json({ success: false, error: "邮箱不能为空" }, 400);
  }

  const code = Math.floor(100000 + Math.random() * 900000).toString();
  const expires = Date.now() + 10 * 60 * 1000;

  try {
    console.log(`准备为 ${email} 执行 Upsert 操作: ${code}`);
    // 使用 ON CONFLICT 确保 email 唯一并更新
    await c.env.fantacy_db.prepare(`
      INSERT INTO codes (email, code, expires) 
      VALUES (?, ?, ?)
      ON CONFLICT(email) DO UPDATE SET 
        code = excluded.code,
        expires = excluded.expires
    `).bind(email, code, expires).run();
    
    console.log("数据库写入成功 (Upsert)");

    console.log("开始调用邮件发送函数...");
    const sent = await sendGmail(email, code, c.env);
    
    if (sent) {
      console.log("邮件发送成功！");
      return c.json({ success: true });
    } else {
      console.error("邮件服务返回失败状态");
      return c.json({ success: false, error: "邮件服务调用失败" }, 500);
    }
  } catch (e: any) {
    console.error("后端捕获到异常:", e.message);
    if (e.message.includes("no such table")) {
      return c.json({ success: false, error: "数据库表 codes 不存在，请先创建" }, 500);
    }
    return c.json({ success: false, error: `服务器内部错误: ${e.message}` }, 500);
  }
});

// --- 2. 注册接口 (密码哈希化处理) ---
auth.post("/register", async (c) => {
  const { username, password, code } = await c.req.json(); 
  
  // 验证码校验
  const record = await c.env.fantacy_db.prepare(
    "SELECT * FROM codes WHERE email = ? AND code = ?"
  ).bind(username, code).first<{ expires: number }>();

  if (!record || record.expires < Date.now()) {
    return c.json({ error: "验证码错误或已失效" }, 400);
  }

  try {
    // 🌟 关键：对密码进行加密后再存入数据库
    const hashedPassword = await hashPassword(password, c.env);

    await c.env.fantacy_db.prepare(
      "INSERT INTO user (email, password, status) VALUES (?, ?, 0)"
    ).bind(username, hashedPassword).run();
    
    return c.json({ success: true });
  } catch (e: any) {
    const existing = await c.env.fantacy_db.prepare(
      "SELECT status FROM user WHERE email = ?"
    ).bind(username).first<{status: number}>();

    if (existing && existing.status === 1) {
      // 🌟 重新激活逻辑：同样需要对新设定的密码加密
      const hashedPassword = await hashPassword(password, c.env);
      await c.env.fantacy_db.prepare(
        "UPDATE user SET password = ?, status = 0 WHERE email = ?"
      ).bind(hashedPassword, username).run();
      return c.json({ success: true, message: "账号已重新激活" });
    }

    return c.json({ error: "该邮箱已被注册" }, 400);
  } finally {
    await c.env.fantacy_db.prepare("DELETE FROM codes WHERE email = ?").bind(username).run();
  }
});

// --- 3. 重置密码接口 (密码哈希化处理) ---
auth.post("/reset-password", async (c) => {
  const { username, password, code } = await c.req.json();

  const record = await c.env.fantacy_db.prepare(
    "SELECT * FROM codes WHERE email = ? AND code = ?"
  ).bind(username, code).first<{ expires: number }>();

  if (!record || record.expires < Date.now()) {
    return c.json({ error: "验证码验证失败" }, 400);
  }

  // 🌟 关键：对新密码进行加密
  const hashedPassword = await hashPassword(password, c.env);

  const res = await c.env.fantacy_db.prepare(
    "UPDATE user SET password = ? WHERE email = ?"
  ).bind(hashedPassword, username).run();

  if (res.meta.changes === 0) return c.json({ error: "未找到该用户" }, 404);

  await c.env.fantacy_db.prepare("DELETE FROM codes WHERE email = ?").bind(username).run();
  return c.json({ success: true });
});

// --- 4. 登录接口 (哈希比对) ---
auth.post("/login", async (c) => {
  const { username, password } = await c.req.json();
  
  // 🌟 关键：将用户输入的明文密码进行哈希，然后去数据库匹配已有的哈希串
  const hashedPassword = await hashPassword(password, c.env);

  const user = await c.env.fantacy_db.prepare(
    "SELECT id FROM user WHERE email = ? AND password = ? AND status = 0"
  ).bind(username, hashedPassword).first<{ id: number }>();

  if (!user) {
    return c.json({ error: "账号或密码错误" }, 401);
  }
  
  const token = await sign({ id: user.id, username }, c.env.JWT_SECRET, "HS256");
  return c.json({ token });
});

// --- 5. 注销账号 (保持不变) ---
auth.post("/delete-account", async (c) => {
  const { username } = await c.req.json(); 
  
  if (!username) return c.json({ success: false, error: "参数缺失" }, 400);

  try {
    const res = await c.env.fantacy_db.prepare(
      "UPDATE user SET status = 1 WHERE email = ?"
    ).bind(username).run();

    if (res.meta.changes === 0) return c.json({ success: false, error: "用户不存在" }, 404);

    return c.json({ success: true, message: "账号已成功注销" });
  } catch (e: any) {
    return c.json({ success: false, error: "注销失败" }, 500);
  }
});

export default auth;