package com.springboot.intellrecipe.recipe.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.recipe.client.DeepSeekClient;
import com.springboot.intellrecipe.recipe.dto.ChatRequest;
import com.springboot.intellrecipe.recipe.dto.ChatResponse;
import com.springboot.intellrecipe.recipe.entity.ChatMessage;
import com.springboot.intellrecipe.recipe.entity.ChatSession;
import com.springboot.intellrecipe.recipe.mapper.ChatMessageMapper;
import com.springboot.intellrecipe.recipe.mapper.ChatSessionMapper;
import com.springboot.intellrecipe.recipe.service.ChatService;
import com.springboot.intellrecipe.recipe.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Resource
    private DeepSeekClient deepSeekClient;

    @Resource
    private ToolExecutor toolExecutor;

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    private static final String SYSTEM_PROMPT = "你是一个智能食谱推荐助手，名叫小厨。你可以帮助用户：\n" +
            "1. 根据口味偏好和现有食材推荐食谱\n" +
            "2. 提供详细的烹饪步骤\n" +
            "3. 记住用户的饮食偏好\n\n" +
            "推荐的食谱必须来自数据库中的真实食谱。当用户表达喜好或忌口时，使用 manage_preference 工具记录。";

    @Override
    public ChatResponse chat(ChatRequest request, Long userId) {
        Long sessionId = request.getSessionId();
        
        // 如果没有 sessionId，创建新会话
        if (sessionId == null) {
            sessionId = createSession(userId);
        }
        
        // 保存用户消息
        saveMessage(sessionId, "user", request.getMessage(), null, null);
        
        // 构建对话历史
        String messages = buildMessages(sessionId);
        
        // 获取工具定义
        String tools = toolExecutor.getToolsDefinition();
        
        // 调用 DeepSeek API
        String response = deepSeekClient.chat(messages, tools);
        JSONObject assistantMessage = deepSeekClient.extractAssistantMessage(response);
        
        // 处理工具调用
        int maxIterations = 5;
        int iteration = 0;
        
        while (deepSeekClient.hasToolCalls(assistantMessage) && iteration < maxIterations) {
            iteration++;
            
            JSONArray toolCalls = assistantMessage.getJSONArray("tool_calls");
            String assistantContent = assistantMessage.getStr("content");
            String assistantToolCalls = toolCalls.toString();
            
            // 保存 assistant 消息（带工具调用）
            saveMessage(sessionId, "assistant", assistantContent, assistantToolCalls, null);
            
            // 执行工具调用
            List<JSONObject> toolResults = new ArrayList<>();
            for (int i = 0; i < toolCalls.size(); i++) {
                JSONObject toolCall = toolCalls.getJSONObject(i);
                String toolCallId = toolCall.getStr("id");
                JSONObject function = toolCall.getJSONObject("function");
                String toolName = function.getStr("name");
                String arguments = function.getStr("arguments");
                
                Object result = toolExecutor.execute(toolName, arguments, userId);
                
                JSONObject toolResult = new JSONObject();
                toolResult.set("tool_call_id", toolCallId);
                toolResult.set("role", "tool");
                toolResult.set("content", JSONUtil.toJsonStr(result));
                toolResults.add(toolResult);
                
                // 保存工具结果
                saveMessage(sessionId, "tool", JSONUtil.toJsonStr(result), null, toolCallId);
            }
            
            // 重新构建消息历史（包含工具结果）
            messages = buildMessagesWithToolResults(sessionId, toolResults);
            
            // 再次调用 DeepSeek API
            response = deepSeekClient.chat(messages, tools);
            assistantMessage = deepSeekClient.extractAssistantMessage(response);
        }
        
        // 保存最终的 assistant 回复
        String finalContent = assistantMessage.getStr("content");
        saveMessage(sessionId, "assistant", finalContent, null, null);
        
        return new ChatResponse(sessionId, finalContent);
    }
    
    private Long createSession(Long userId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle("新对话");
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setDeleted(0);
        chatSessionMapper.insert(session);
        return session.getId();
    }
    
    private void saveMessage(Long sessionId, String role, String content, String toolCalls, String toolCallId) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setToolCalls(toolCalls);
        message.setToolCallId(toolCallId);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
    }
    
    private String buildMessages(Long sessionId) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime)
        );
        
        JSONArray messageArray = new JSONArray();
        
        // 添加系统提示
        JSONObject systemMsg = new JSONObject();
        systemMsg.set("role", "system");
        systemMsg.set("content", SYSTEM_PROMPT);
        messageArray.add(systemMsg);
        
        // 添加历史消息
        for (ChatMessage msg : messages) {
            JSONObject msgObj = new JSONObject();
            msgObj.set("role", msg.getRole());
            msgObj.set("content", msg.getContent());
            messageArray.add(msgObj);
        }
        
        return messageArray.toString();
    }
    
    private String buildMessagesWithToolResults(Long sessionId, List<JSONObject> toolResults) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime)
        );
        
        JSONArray messageArray = new JSONArray();
        
        // 添加系统提示
        JSONObject systemMsg = new JSONObject();
        systemMsg.set("role", "system");
        systemMsg.set("content", SYSTEM_PROMPT);
        messageArray.add(systemMsg);
        
        // 添加历史消息
        for (ChatMessage msg : messages) {
            JSONObject msgObj = new JSONObject();
            msgObj.set("role", msg.getRole());
            msgObj.set("content", msg.getContent());
            
            if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null) {
                msgObj.set("tool_calls", JSONUtil.parseArray(msg.getToolCalls()));
            }
            
            messageArray.add(msgObj);
        }
        
        // 添加工具结果
        for (JSONObject toolResult : toolResults) {
            messageArray.add(toolResult);
        }
        
        return messageArray.toString();
    }
}