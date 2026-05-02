export const extractJSON = (text: string) => {
    const match = text.match(/\{[\s\S]*\}/);
    try {
        return match ? JSON.parse(match[0]) : null;
    } catch {
        return null;
    }
};