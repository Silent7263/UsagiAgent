/**
 * UsagiAgent 品牌素材：取自桌面 usagi 文件夹的 Chiikawa 免扣图案。
 * 统一通过 public/usagi/ 下的规范文件名引用（已压缩为 256px）。
 */
export const USAGI_ASSETS = {
  /** 兔子 - 品牌主吉祥物（Usagi = 兔子） */
  mascot: "/usagi/usagi-16.png",
  /** 戴蓝帽委屈脸 - 空状态/撒娇 */
  sad: "/usagi/usagi-01.png",
  /** 捧云朵 - 对话/聊天 */
  cloud: "/usagi/usagi-10.png",
  /** 握五星俏皮 - 智能体/活力 */
  star: "/usagi/usagi-11.png",
  /** 星星+冰淇淋 - 加载/心情 */
  starCone: "/usagi/usagi-12.png",
  /** 月亮 - 思考/晚安 */
  moon: "/usagi/usagi-13.png",
  /** 云朵贝雷帽 - 知识/书卷气 */
  cloudBeret: "/usagi/usagi-14.png",
  /** 小狗贝雷帽 - 陪伴 */
  puppy: "/usagi/usagi-15.png",
  /** 复古相机 - 记录 */
  camera: "/usagi/usagi-17.png",
  /** 星星弓箭 - 目标/行动 */
  starBow: "/usagi/usagi-18.png",
  /** 礼物 - 惊喜/开始 */
  gift: "/usagi/usagi-19.png",
} as const;

export type UsagiAssetKey = keyof typeof USAGI_ASSETS;
