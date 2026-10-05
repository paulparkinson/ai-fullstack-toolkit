package com.oracle.ai.fullstack.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.ai.fullstack.execution.*;
import com.oracle.ai.fullstack.projection.ToolProjections;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;

/** Opt-in web adapters. The application supplies executable bindings, resources and identity. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "fullstack.protocols.enabled", havingValue = "true")
public class ProtocolAutoConfiguration {
    @Bean @ConditionalOnMissingBean CallerResolver callerResolver() {
        return request -> {
            if (request.getUserPrincipal() == null) throw new IllegalArgumentException("Authenticated caller required");
            return request.getUserPrincipal().getName();
        };
    }
    @Bean @ConditionalOnMissingBean AppResources appResources() { return new AppResources(Map.of()); }
    @Bean HttpServletStreamableServerTransportProvider mcpTransport(CallerResolver callers) {
        return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp")
            .contextExtractor(request -> McpTransportContext.create(Map.of("actor", callers.actor(request))))
            .build();
    }
    @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(HttpServletStreamableServerTransportProvider transport) {
        var bean = new ServletRegistrationBean<>(transport, "/mcp"); bean.setAsyncSupported(true); return bean;
    }
    @Bean(destroyMethod = "closeGracefully") McpSyncServer fullstackMcpServer(
            HttpServletStreamableServerTransportProvider transport, Operations operations, AppResources resources, ObjectMapper json) {
        var server = McpServer.sync(transport).serverInfo("ai-fullstack-toolkit", "0.1.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).resources(false, false).build()).build();
        for (var b : operations.bindings()) {
            var d = b.definition();
            if (!d.mcp().enabled()) continue;
            if (d.mcpApp().enabled() && !resources.html().containsKey(d.mcpApp().resourceUri()))
                throw new IllegalArgumentException("Missing MCP App resource: " + d.mcpApp().resourceUri());
            var descriptor = new LinkedHashMap<>(ToolProjections.mcpDescriptor(d));
            descriptor.remove("statement"); // SQL is an upstream toolkit concern, never exposed as an executable input.
            var tool = json.convertValue(descriptor, McpSchema.Tool.class);
            server.addTool(McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
                try {
                    var context = new Operations.Context((String) exchange.transportContext().get("actor"), UUID.randomUUID().toString());
                    var result = operations.execute(d.id(), request.arguments(), Operations.Surface.MCP, context);
                    return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(json.writeValueAsString(result))), false, result, null);
                } catch (Exception e) {
                    String message = e instanceof IllegalArgumentException ? e.getMessage() : "Backend failed; no fallback attempted";
                    return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), true, null, null);
                }
            }).build());
        }
        resources.html().forEach((uri, html) -> server.addResource(new McpServerFeatures.SyncResourceSpecification(
            McpSchema.Resource.builder().uri(uri).name(uri).mimeType(AppResources.MIME_TYPE).build(),
            (exchange, request) -> new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(uri, AppResources.MIME_TYPE, html,
                Map.of("ui", Map.of("csp", Map.of("connectDomains", List.of(), "resourceDomains", List.of())))))))));
        return server;
    }
    @Bean A2aEndpoint a2aEndpoint(Operations operations, CallerResolver callers, ObjectProvider<ApprovalGate> approvals,
            @Value("${fullstack.public-base-url}") String baseUrl) {
        return new A2aEndpoint(operations, callers, approvals.getIfAvailable(), baseUrl);
    }
    @Bean FilterRegistrationBean<OncePerRequestFilter> protocolGuard(CallerResolver callers,
            @Value("${fullstack.public-base-url}") String baseUrl) {
        URI base = URI.create(baseUrl);
        String expectedOrigin = base.getScheme() + "://" + base.getRawAuthority();
        var filter = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                String origin = request.getHeader("Origin");
                if (origin != null && !origin.equals(expectedOrigin)) { response.sendError(403, "Origin not allowed"); return; }
                try { callers.actor(request); }
                catch (RuntimeException e) { response.sendError(401, "Authenticated caller required"); return; }
                chain.doFilter(request, response);
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.addUrlPatterns("/mcp", "/a2a"); registration.setOrder(-100); registration.setAsyncSupported(true);
        return registration;
    }
}
