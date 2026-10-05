package com.oracle.ai.fullstack.starter;

import com.oracle.ai.fullstack.execution.*;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;

/** Delegates execution to the existing Oracle Database MCP Java Toolkit, never reimplements SQL. */
public final class OracleToolkitBackend implements Backend {
    private final McpSyncClient client;
    private final Set<String> allowedTools;
    public OracleToolkitBackend(McpSyncClient initializedClient, Set<String> allowedTools) {
        this.client = Objects.requireNonNull(initializedClient); this.allowedTools = Set.copyOf(allowedTools);
    }
    @Override public Operations.Result call(String operation, Map<String,Object> arguments, Operations.Context context) {
        if (!allowedTools.contains(operation)) throw new IllegalArgumentException("Toolkit tool is not allowlisted");
        var result = client.callTool(new McpSchema.CallToolRequest(operation, arguments));
        if (Boolean.TRUE.equals(result.isError())) throw new IllegalStateException("Oracle MCP toolkit returned an error; no fallback attempted");
        var data = new LinkedHashMap<String,Object>();
        data.put("content", result.content());
        if (result.structuredContent() != null) data.put("structuredContent", result.structuredContent());
        // This is a local correlation ID, not an assertion of an independently audited database query.
        return new Operations.Result(data, new Operations.Evidence("oracle-db-mcp-java-toolkit", false, context.requestId()));
    }
}
