import { USAGI_ASSETS, type UsagiAssetKey } from "../usagiAssets";

/** 对字符串做 32 位哈希，保证同一 id 总是得到同一结果 */
const hashOf = (s: string): number => {
  let hash = 0;
  for (let i = 0; i < s.length; i++) {
    hash = (hash << 5) - hash + s.charCodeAt(i);
    hash = hash & hash; // Convert to 32bit integer
  }
  return hash;
};

export const getAgentEmoji = (agentId: string): string => {
  // 使用 agent id 的哈希值来选择 emoji，确保同一个 agent 总是显示相同的 emoji
  const EMOJI_LIST = [
    "🤖",
    "🎯",
    "🚀",
    "💡",
    "🔮",
    "⚡",
    "🌟",
    "🎨",
    "🔧",
    "📚",
  ];
  const index = Math.abs(hashOf(agentId)) % EMOJI_LIST.length;
  return EMOJI_LIST[index];
};

/**
 * 按 agent id 从 Usagi 素材中确定性分配一张头像图。
 * 新建智能体即自动获得一张随机分布的 Usagi 头像，且同一智能体刷新后保持不变。
 */
export const getAgentUsagi = (agentId: string): string => {
  const keys = Object.keys(USAGI_ASSETS) as UsagiAssetKey[];
  const index = Math.abs(hashOf(agentId)) % keys.length;
  return USAGI_ASSETS[keys[index]];
};

export const getKnowledgeBaseEmoji = (knowledgeBaseId: string): string => {
  // 知识库相关的 emoji 列表
  const KNOWLEDGE_BASE_EMOJI_LIST = [
    "📚",
    "📖",
    "📝",
    "📋",
    "📑",
    "📄",
    "📃",
    "📊",
    "📈",
    "📉",
  ];
  // 使用知识库 id 的哈希值来选择 emoji，确保同一个知识库总是显示相同的 emoji
  const index = Math.abs(hashOf(knowledgeBaseId)) % KNOWLEDGE_BASE_EMOJI_LIST.length;
  return KNOWLEDGE_BASE_EMOJI_LIST[index];
};

export const formatDateTime = (dateString?: string): string => {
  if (!dateString) return "";
  const date = new Date(dateString);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  
  if (days === 0) {
    const hours = Math.floor(diff / (1000 * 60 * 60));
    if (hours === 0) {
      const minutes = Math.floor(diff / (1000 * 60));
      return minutes <= 0 ? "刚刚" : `${minutes}分钟前`;
    }
    return `${hours}小时前`;
  } else if (days === 1) {
    return "昨天";
  } else if (days < 7) {
    return `${days}天前`;
  } else {
    return date.toLocaleDateString("zh-CN", {
      month: "short",
      day: "numeric",
    });
  }
};
