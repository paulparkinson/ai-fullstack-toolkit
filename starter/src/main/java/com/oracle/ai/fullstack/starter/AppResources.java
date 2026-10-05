package com.oracle.ai.fullstack.starter;

import java.util.Map;

/** Bundled, application-owned HTML only; clients cannot register arbitrary resource URLs. */
public record AppResources(Map<String, String> html) {
    public static final String MIME_TYPE = "text/html;profile=mcp-app";
    public AppResources {
        html = Map.copyOf(html);
        if (html.keySet().stream().anyMatch(uri -> !uri.startsWith("ui://")))
            throw new IllegalArgumentException("App resources must use ui:// URIs");
    }
}
