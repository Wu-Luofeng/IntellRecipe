package com.springboot.intellrecipe.recipe.client;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

@Slf4j
@Component
public class DeepSeekClient {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Resource
    private RestTemplate restTemplate;

    public String chat(String messages, String tools) {
        String url = baseUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("messages", JSONUtil.parseArray(messages));
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", JSONUtil.parseArray(tools));
        }

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("DeepSeek API call failed", e);
            throw new RuntimeException("DeepSeek API call failed: " + e.getMessage());
        }
    }

    public JSONObject extractAssistantMessage(String response) {
        JSONObject json = JSONUtil.parseObj(response);
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("DeepSeek returned empty choices");
        }
        return choices.getJSONObject(0).getJSONObject("message");
    }

    public boolean hasToolCalls(JSONObject message) {
        JSONArray toolCalls = message.getJSONArray("tool_calls");
        return toolCalls != null && !toolCalls.isEmpty();
    }
}