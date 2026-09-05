import React, { useEffect, useState } from "react";
import {
  Button,
  Input,
  Modal,
  Radio,
  Segmented,
  Select,
  Upload,
  message as antdMessage,
} from "antd";
import TextArea from "antd/es/input/TextArea";
import {
  CloudUploadOutlined,
  FileTextOutlined,
  CopyOutlined,
  CheckOutlined,
  RobotOutlined,
  LinkOutlined,
  LoadingOutlined,
} from "@ant-design/icons";
import type { UploadProps } from "antd";
import XMarkdown from "@ant-design/x-markdown";
import {
  processExternalChat,
  fetchExternalChat,
  type ExternalChatAction,
  type AgentVO,
} from "../../api/api.ts";

interface ImportConversationModalProps {
  open: boolean;
  onClose: () => void;
  agents: AgentVO[];
  /** 接替任务成功后回调（chatSessionId 用于跳转会话） */
  onTakeoverSuccess: (chatSessionId: string) => void;
}

const ImportConversationModal: React.FC<ImportConversationModalProps> = ({
  open,
  onClose,
  agents,
  onTakeoverSuccess,
}) => {
  const [conversationText, setConversationText] = useState("");
  const [action, setAction] = useState<ExternalChatAction>("SUMMARIZE");
  const [agentId, setAgentId] = useState<string>();
  const [followUp, setFollowUp] = useState("");
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // 输入方式：text 粘贴/上传；link 分享链接
  const [inputMode, setInputMode] = useState<"text" | "link">("text");
  const [linkUrl, setLinkUrl] = useState("");
  const [fetchingLink, setFetchingLink] = useState(false);

  // 打开弹窗时重置状态
  useEffect(() => {
    if (open) {
      setConversationText("");
      setAction("SUMMARIZE");
      setAgentId(undefined);
      setFollowUp("");
      setSummary(null);
      setCopied(false);
      setInputMode("text");
      setLinkUrl("");
      setFetchingLink(false);
    }
  }, [open]);

  // 解析分享链接，成功后填充对话文本并切回文本视图
  const handleFetchLink = async () => {
    if (!linkUrl.trim()) {
      antdMessage.warning("请输入分享链接");
      return;
    }
    setFetchingLink(true);
    try {
      const resp = await fetchExternalChat(linkUrl.trim());
      setConversationText(resp.conversationText);
      setInputMode("text");
      antdMessage.success(
        `已从 ${resp.source} 解析 ${resp.messageCount ?? 0} 条消息`,
      );
    } catch (error) {
      console.error("解析分享链接失败:", error);
      // 错误提示已由 http.ts 统一弹出
    } finally {
      setFetchingLink(false);
    }
  };

  // 读取上传的对话文件（.txt / .md），追加到文本框
  const handleFileRead: UploadProps["beforeUpload"] = (file) => {
    if (!file) return false;
    const reader = new FileReader();
    reader.onload = () => {
      const content = String(reader.result ?? "");
      setConversationText((prev) => (prev ? prev + "\n" + content : content));
      antdMessage.success(`已读取文件：${file.name}`);
    };
    reader.onerror = () => {
      antdMessage.error("文件读取失败，请重试");
    };
    reader.readAsText(file, "utf-8");
    return false; // 阻止自动上传
  };

  const handleSubmit = async () => {
    if (!conversationText.trim()) {
      antdMessage.warning("请先粘贴或上传对话内容");
      return;
    }
    if (action === "TAKEOVER" && !agentId) {
      antdMessage.warning("接替任务需要选择一个智能体");
      return;
    }
    setLoading(true);
    try {
      const resp = await processExternalChat({
        conversationText: conversationText.trim(),
        action,
        agentId: action === "TAKEOVER" ? agentId : undefined,
        followUp: action === "TAKEOVER" && followUp.trim() ? followUp.trim() : undefined,
      });
      if (action === "SUMMARIZE") {
        setSummary(resp.summary || "");
      } else {
        antdMessage.success(
          `已导入 ${resp.importedMessages ?? 0} 条消息，智能体开始接替任务`,
        );
        if (resp.chatSessionId) {
          onTakeoverSuccess(resp.chatSessionId);
        }
        onClose();
      }
    } catch (error) {
      console.error("处理外部对话失败:", error);
      antdMessage.error("处理失败，请重试");
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = async () => {
    if (!summary) return;
    try {
      await navigator.clipboard.writeText(summary);
      setCopied(true);
      antdMessage.success("已复制到剪贴板");
      setTimeout(() => setCopied(false), 2000);
    } catch {
      antdMessage.error("复制失败，请手动选择复制");
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title="导入外部对话"
      width={720}
      centered
      footer={
        summary ? (
          <Button onClick={onClose} type="primary">
            完成
          </Button>
        ) : (
          <>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" loading={loading} onClick={handleSubmit}>
              开始处理
            </Button>
          </>
        )
      }
    >
      {summary ? (
        // 整理对话结果
        <div>
          <div className="flex items-center justify-between mb-3">
            <div className="text-sm text-gray-500">整理结果</div>
            <Button
              size="small"
              icon={copied ? <CheckOutlined /> : <CopyOutlined />}
              onClick={handleCopy}
            >
              {copied ? "已复制" : "复制"}
            </Button>
          </div>
          <div className="max-h-[420px] overflow-y-auto border border-gray-200 rounded-lg p-4 bg-gray-50">
            <XMarkdown>{summary}</XMarkdown>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {/* 输入方式切换 */}
          <div>
            <label className="block text-gray-700 font-medium mb-2">
              导入方式
            </label>
            <Segmented
              value={inputMode}
              onChange={(value) => setInputMode(value as "text" | "link")}
              options={[
                { label: "粘贴 / 上传文件", value: "text" },
                { label: "分享链接", value: "link" },
              ]}
            />
          </div>

          {/* 链接导入 */}
          {inputMode === "link" ? (
            <div>
              <label className="block text-gray-700 font-medium mb-2">
                分享链接
              </label>
              <div className="flex gap-2">
                <Input
                  prefix={<LinkOutlined className="text-gray-400" />}
                  placeholder="例如：https://chat.deepseek.com/share/xxxxxx"
                  value={linkUrl}
                  onChange={(e) => setLinkUrl(e.target.value)}
                  onPressEnter={handleFetchLink}
                />
                <Button
                  type="primary"
                  onClick={handleFetchLink}
                  loading={fetchingLink}
                  icon={fetchingLink ? <LoadingOutlined /> : undefined}
                >
                  解析
                </Button>
              </div>
              <div className="text-xs text-gray-400 mt-2">
                当前支持 DeepSeek 分享链接；解析成功后对话内容会自动填入下方文本框，可修改后再处理
              </div>
            </div>
          ) : (
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-gray-700 font-medium">
                  对话内容
                </label>
                <div className="flex items-center gap-3">
                  <span className="text-xs text-gray-400">
                    {conversationText.length} 字符
                  </span>
                  <Upload
                    beforeUpload={handleFileRead}
                    showUploadList={false}
                    accept=".txt,.md,.markdown"
                  >
                    <Button size="small" icon={<CloudUploadOutlined />}>
                      上传对话文件
                    </Button>
                  </Upload>
                </div>
              </div>
              <TextArea
                rows={9}
                placeholder={
                  "把与其他大模型（ChatGPT / Claude / DeepSeek 等）的对话复制粘贴到这里，或上传 .txt / .md 文件。\n\n支持识别常见说话人前缀，如：\n我：……\nChatGPT：……\n用户: ……\nAssistant: ……"
                }
                value={conversationText}
                onChange={(e) => setConversationText(e.target.value)}
              />
            </div>
          )}

          {/* 操作类型 */}
          <div>
            <label className="block text-gray-700 font-medium mb-2">
              操作类型
            </label>
            <Radio.Group
              value={action}
              onChange={(e) => setAction(e.target.value)}
              optionType="button"
              buttonStyle="solid"
            >
              <Radio value="SUMMARIZE">整理对话（生成摘要）</Radio>
              <Radio value="TAKEOVER">接替任务（继续推进）</Radio>
            </Radio.Group>
          </div>

          {/* 接替任务配置 */}
          {action === "TAKEOVER" && (
            <div className="flex flex-col gap-4">
              <div>
                <label className="block text-gray-700 font-medium mb-2">
                  由哪个智能体接替
                  <span className="text-red-500 ml-1">*</span>
                </label>
                <Select
                  className="w-full"
                  placeholder="请选择智能体"
                  value={agentId}
                  onChange={setAgentId}
                  options={agents.map((agent) => ({
                    value: agent.id,
                    label: agent.name,
                  }))}
                  suffixIcon={<RobotOutlined />}
                />
              </div>
              <div>
                <label className="block text-gray-700 font-medium mb-2">
                  接替指令
                  <span className="text-gray-400 text-xs ml-1">
                    （可选，不填则默认让智能体理解上下文并继续推进）
                  </span>
                </label>
                <TextArea
                  rows={2}
                  placeholder="例如：继续完成上面的方案设计，并输出完整文档"
                  value={followUp}
                  onChange={(e) => setFollowUp(e.target.value)}
                />
              </div>
            </div>
          )}

          <div className="text-xs text-gray-400 flex items-center gap-1">
            <FileTextOutlined />
            接替任务会新建一个会话，导入对话记录后由所选智能体继续处理
          </div>
        </div>
      )}
    </Modal>
  );
};

export default ImportConversationModal;
