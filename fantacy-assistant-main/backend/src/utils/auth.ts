import { verify } from 'hono/jwt';
export async function getUserId(c: any): Promise<number | null> {
  const authHeader = c.req.header('Authorization');
  if (!authHeader) return null;
  const token = authHeader.replace("Bearer ", "");
  try {
    const payload = await verify(token, c.env.JWT_SECRET, "HS256");
    return payload.id as number;
  } catch {
    return null;
  }
}