export interface ChatMessage {
    role: 'system' | 'user' | 'assistant';
    content: string;
}

export interface UserProfile {
    user_id: number;
    nickname: string | null;
    gender: string | null;
    birthday: string | null;
    occupation: string | null;
    mbti: string | null;
    hobbies: string | null;
    communication_style: string | null;
    goals: string | null;
    taboos: string | null;
    bio: string | null;
}

export interface AssistantProfile {
    user_id: number;
    name: string | null;
    birthday: string | null;
    role_identity: string | null;
    personality_tags: string | null;
    speaking_style: string | null;
    expertise: string | null;
    values_system: string | null;
    background_story: string | null;
}

export interface EvolutionProfile {
    user_id: number;
    content: string;
    updated_at: string;
}

export interface EvolutionResult {
    user_evolution: string;
    bot_evolution: string;
}