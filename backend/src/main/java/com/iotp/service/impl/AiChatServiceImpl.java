package com.iotp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotp.entity.AiChatMessage;
import com.iotp.mapper.AiChatMessageMapper;
import com.iotp.service.AiChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.base-url}")
    private String baseUrl;

    @Value("${ai.deepseek.model}")
    private String model;

    @Autowired
    private AiChatMessageMapper messageMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 最大历史轮数 */
    private static final int MAX_HISTORY_ROUNDS = 15;

    @Override
    public String chatStream(Long userId, String message, Map<String, Object> context,
                             Consumer<String> callback) {
        try {
            // 1. 保存用户消息
            saveMessage(userId, "user", message, context);

            // 2. 构建消息列表
            List<Map<String, String>> messages = new ArrayList<>();

            // 系统提示词（根据角色和上下文动态调整）
            String systemPrompt = buildSystemPrompt(context);
            messages.add(buildMsg("system", systemPrompt));

            // 加载 DB 中的历史对话
            List<AiChatMessage> dbHistory = loadRecentHistory(userId, MAX_HISTORY_ROUNDS * 2);
            for (AiChatMessage h : dbHistory) {
                messages.add(buildMsg(h.getRole(), h.getContent()));
            }

            // 如果有文件内容上下文，追加到用户消息
            if (context != null && context.containsKey("fileContent")) {
                String fileText = (String) context.get("fileContent");
                String fileName = (String) context.getOrDefault("fileName", "未知文件");
                message = "【用户上传了文件：" + fileName + "，内容如下】\n" +
                        fileText.substring(0, Math.min(fileText.length(), 8000)) +
                        "\n【用户问题】\n" + message;
            }

            // 添加当前消息
            messages.add(buildMsg("user", message));

            // 3. 调用 DeepSeek API
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", true);
            body.put("temperature", 0.7);
            body.put("max_tokens", 2048);

            String requestJson = objectMapper.writeValueAsString(body);

            URL url = new URL(baseUrl + "/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestJson.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                BufferedReader er = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder eb = new StringBuilder();
                String l;
                while ((l = er.readLine()) != null) eb.append(l);
                er.close();
                log.error("DeepSeek API error {}: {}", responseCode, eb);
                String err = "AI 服务暂时不可用";
                callback.accept(err);
                saveMessage(userId, "assistant", err, context);
                return err;
            }

            StringBuilder fullReply = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode choices = node.get("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String c = delta.get("content").asText();
                                if (c != null && !c.isEmpty()) {
                                    fullReply.append(c);
                                    callback.accept(c);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            reader.close();
            conn.disconnect();

            // 4. 保存 AI 回复
            String reply = fullReply.toString();
            if (reply.isEmpty()) reply = "抱歉，我没有理解你的问题。";
            saveMessage(userId, "assistant", reply, context);

            log.info("AI chat done, userId={}, replyLen={}", userId, reply.length());
            return reply;

        } catch (Exception e) {
            log.error("AI chat error", e);
            String err = "AI 服务异常：" + e.getMessage();
            callback.accept(err);
            saveMessage(userId, "assistant", err, context);
            return err;
        }
    }

    @Override
    public List<Map<String, Object>> getHistory(Long userId, int limit) {
        List<AiChatMessage> list = loadRecentHistory(userId, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiChatMessage m : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", m.getRole());
            item.put("content", m.getContent());
            item.put("contextType", m.getContextType());
            item.put("contextId", m.getContextId());
            item.put("time", m.getCreateTime() != null ? m.getCreateTime().toString() : "");
            result.add(item);
        }
        return result;
    }

    @Override
    public void clearHistory(Long userId) {
        QueryWrapper<AiChatMessage> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        messageMapper.delete(qw);
        log.info("User {} cleared AI chat history", userId);
    }

    // ==================== 私有方法 ====================

    private void saveMessage(Long userId, String role, String content, Map<String, Object> context) {
        AiChatMessage msg = new AiChatMessage();
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        if (context != null) {
            msg.setContextType((String) context.get("contextType"));
            if (context.get("contextId") instanceof Number) {
                msg.setContextId(((Number) context.get("contextId")).longValue());
            }
        }
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    private List<AiChatMessage> loadRecentHistory(Long userId, int limit) {
        QueryWrapper<AiChatMessage> qw = new QueryWrapper<>();
        qw.eq("user_id", userId)
          .orderByDesc("create_time")
          .last("LIMIT " + limit);
        List<AiChatMessage> list = messageMapper.selectList(qw);
        Collections.reverse(list); // 时间正序
        return list;
    }

    private String buildSystemPrompt(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是智能在线教学平台的AI学习助手。");

        // 角色适配
        if (context != null && context.containsKey("roleName")) {
            String roleName = (String) context.get("roleName");
            if ("学生".equals(roleName)) {
                sb.append("你正在帮助一位学生，请用耐心、易懂的方式解答问题，鼓励学生思考。");
            } else if ("教师".equals(roleName)) {
                sb.append("你正在帮助一位教师，可以帮助备课、出题、分析班级情况、设计实验等。");
            } else if ("管理员".equals(roleName)) {
                sb.append("你正在帮助系统管理员，可以解答系统配置、数据监控、用户管理等问题。");
            }
        }

        // 页面上下文
        if (context != null && context.containsKey("contextType")) {
            String ctxType = (String) context.get("contextType");
            String ctxName = (String) context.getOrDefault("contextName", "");
            String ctxDetail = (String) context.getOrDefault("contextDetail", "");

            if ("COURSE".equals(ctxType)) {
                sb.append("用户当前在课程「").append(ctxName).append("」页面。");
                if (!ctxDetail.isEmpty()) sb.append("课程信息：").append(ctxDetail).append("。");
                sb.append("可以围绕该课程内容回答疑问、总结知识点、解释概念。");
            } else if ("TASK".equals(ctxType)) {
                sb.append("用户当前在实验实训「").append(ctxName).append("」页面。");
                sb.append("可以指导实验步骤、讲解原理、帮助排查问题。");
            } else if ("RESOURCE".equals(ctxType)) {
                sb.append("用户当前在教学资源库，正在查看「").append(ctxName).append("」。");
                sb.append("可以推荐相关学习资源、解释文档内容。");
            } else if ("ANALYTICS".equals(ctxType)) {
                sb.append("用户正在查看学情诊断页面。可以帮助分析学习数据、给出学习建议。");
            } else if ("DASHBOARD".equals(ctxType)) {
                sb.append("用户正在首页，可以概括今日学习任务、提醒待办事项。");
            }
        }

        sb.append("回答应简洁有条理，适当使用分点列举。");
        return sb.toString();
    }

    private Map<String, String> buildMsg(String role, String content) {
        Map<String, String> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }
}
