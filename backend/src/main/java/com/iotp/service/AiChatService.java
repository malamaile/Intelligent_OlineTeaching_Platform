package com.iotp.service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 对话服务接口
 */
public interface AiChatService {

    /**
     * 流式对话
     */
    String chatStream(Long userId, String message, Map<String, Object> context,
                      Consumer<String> callback);

    /**
     * 获取最近 N 条历史消息
     */
    List<Map<String, Object>> getHistory(Long userId, int limit);

    /**
     * 清空用户对话历史
     */
    void clearHistory(Long userId);
}
