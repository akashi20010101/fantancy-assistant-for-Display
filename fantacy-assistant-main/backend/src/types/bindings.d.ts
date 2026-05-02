export type Bindings = {
  // Cloudflare 资源
  AI: any; 
  fantacy_db: D1Database;
  VECTOR_INDEX: VectorizeIndex;
  
  // 环境变量 / 密钥
  JWT_SECRET: string;
  PASSWORD_SALT: string;
  EMAIL_API_KEY: string;
  MY_EMAIL: string;
  GOOGLE_GENERATIVE_AI_API_KEY: string;
  GLM_API_KEY: string;
};