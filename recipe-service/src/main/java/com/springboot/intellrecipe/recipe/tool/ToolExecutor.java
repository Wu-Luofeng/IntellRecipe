package com.springboot.intellrecipe.recipe.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToolExecutor {

    @Resource
    private List<Tool> tools;

    private final Map<String, Tool> toolMap = new HashMap<>();

    public void init() {
        for (Tool tool : tools) {
            toolMap.put(tool.getName(), tool);
        }
    }

    public Object execute(String toolName, String arguments, Long userId) {
        if (toolMap.isEmpty()) {
            init();
        }
        
        Tool tool = toolMap.get(toolName);
        if (tool == null) {
            log.warn("Unknown tool: {}", toolName);
            return "未知工具: " + toolName;
        }
        
        try {
            log.info("Executing tool: {} with args: {}", toolName, arguments);
            Object result = tool.execute(arguments, userId);
            log.info("Tool {} executed successfully", toolName);
            return result;
        } catch (Exception e) {
            log.error("Tool {} execution failed", toolName, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    public String getToolsDefinition() {
        if (toolMap.isEmpty()) {
            init();
        }
        
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Tool tool : tools) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            
            JSONObject toolDef = new JSONObject();
            toolDef.set("type", "function");
            
            JSONObject function = new JSONObject();
            function.set("name", tool.getName());
            function.set("description", tool.getDescription());
            function.set("parameters", JSONUtil.parseObj(tool.getParameters()));
            
            toolDef.set("function", function);
            sb.append(toolDef.toString());
        }
        sb.append("]");
        
        return sb.toString();
    }
}