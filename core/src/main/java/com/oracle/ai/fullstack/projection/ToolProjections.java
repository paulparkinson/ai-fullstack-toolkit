package com.oracle.ai.fullstack.projection;

import com.oracle.ai.fullstack.model.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generates protocol-facing documents from the common tool definition. */
public final class ToolProjections {
    private ToolProjections() {}
    public static Map<String, Object> mcpDescriptor(ToolDefinition tool) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("name", tool.id());
        descriptor.put("description", tool.description());
        Map<String, Object> properties = new LinkedHashMap<>();
        tool.inputSchema().forEach((key, type) -> properties.put(key, Map.of("type", type)));
        descriptor.put("inputSchema", Map.of("type", "object", "properties", properties,
                "required", tool.inputSchema().keySet().stream().sorted().toList(), "additionalProperties", false));
        if (tool.mcpApp().enabled()) descriptor.put("_meta", Map.of("ui", Map.of("resourceUri", tool.mcpApp().resourceUri())));
        if (!tool.mcp().statement().isBlank()) descriptor.put("statement", tool.mcp().statement());
        return Map.copyOf(descriptor);
    }
    public static Map<String, Object> a2aCard(ToolDefinition tool, String baseUrl) {
        var a = tool.a2a();
        return Map.of("name", a.name(), "description", a.description(), "version", a.version(),
                "url", baseUrl + "/a2a/" + tool.id(), "capabilities", Map.of("streaming", false),
                "skills", List.of(Map.of("id", tool.id(), "name", a.name(), "description", tool.description())));
    }
    public static List<Map<String, Object>> a2uiExample(ToolDefinition tool) {
        String surface = tool.a2ui().surfaceId();
        Map<String, Object> component = Map.of("id", "review", "component", Map.of("Text", Map.of("text",
            Map.of("literalString", "Preview only: " + tool.a2a().name() + ". No executable approval is attached."))));
        return List.of(Map.of("surfaceUpdate", Map.of("surfaceId", surface, "components", List.of(component))),
            Map.of("beginRendering", Map.of("surfaceId", surface, "root", "review")));
    }
    public static Map<String, Object> mcpAppDescriptor(ToolDefinition tool) {
        return Map.of("resource", tool.mcpApp().resourceUri(), "mimeType", "text/html;profile=mcp-app", "tool", tool.id());
    }
}
