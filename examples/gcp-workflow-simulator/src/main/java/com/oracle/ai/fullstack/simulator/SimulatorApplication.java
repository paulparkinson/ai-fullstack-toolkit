package com.oracle.ai.fullstack.simulator;

import com.oracle.ai.fullstack.execution.*;
import com.oracle.ai.fullstack.model.ToolDefinition;
import com.oracle.ai.fullstack.projection.A2uiMessages;
import com.oracle.ai.fullstack.starter.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;

/** Explicitly isolated simulator. There are no Oracle, OAuth or GCP credentials in this app. */
@SpringBootApplication
public class SimulatorApplication {
    public static void main(String[] args) { SpringApplication.run(SimulatorApplication.class, args); }
    @Bean SimulatorData simulatorData() { return new SimulatorData(); }
    @Bean CallerResolver simulatorCaller() { return request -> "local-simulator-user"; }
    @Bean List<Operations.Audit> auditLog() { return Collections.synchronizedList(new ArrayList<>()); }
    @Bean ApprovalGate approvalGate(SimulatorData data) {
        return new ApprovalGate(data::write, Clock.systemUTC(), Duration.ofMinutes(5));
    }
    @Bean AppResources resources() throws IOException {
        String html = new ClassPathResource("static/mcp-app.html").getContentAsString(StandardCharsets.UTF_8);
        return new AppResources(Map.of("ui://inventory/graph", html, "ui://inventory/spatial", html));
    }
    @Bean Operations operations(SimulatorData data, ApprovalGate gate, List<Operations.Audit> auditLog) {
        return new Operations(List.of(
            bind("list-stockout-risk", "List stockout risk as a compact table; no graph or map.", Map.of(), true, true, null, data::read),
            bind("show-supply-chain-graph", "Show a graph only when explicitly requested for one SKU.", Map.of("sku", "string"), true, true, "ui://inventory/graph", data::read),
            bind("show-spatial-hotspots", "Show a spatial map only when explicitly requested for one SKU.", Map.of("sku", "string"), true, true, "ui://inventory/spatial", data::read),
            bind("suggest-inventory-transfers", "Review transfer suggestions. Does not execute a write.", Map.of("minimumRisk", "integer", "limit", "integer"), false, true, null,
                (name, args, ctx) -> {
                    double requestedRisk = ((Number)args.get("minimumRisk")).doubleValue(), requestedLimit = ((Number)args.get("limit")).doubleValue();
                    if (requestedRisk < 0 || requestedRisk > 100 || requestedLimit < 1 || requestedLimit > 3) throw new IllegalArgumentException("Risk must be 0–100; limit 1–3");
                    int min = (int)requestedRisk, limit = (int)requestedLimit;
                    var messages = new ArrayList<Map<String,Object>>();
                    for (var item : data.items()) {
                        if (messages.size() / 2 >= limit) break;
                        if (item.risk() * 100 < min) continue;
                        var p = gate.propose("SIMULATED: move 20 units of " + item.sku() + " from " + item.source() + " to " + item.destination()
                                + ". Review only until you approve. Expires in 5 minutes.", "approve-inventory-transfer",
                            Map.of("sku", item.sku(), "quantity", 20, "source", item.source(), "destination", item.destination()), ctx);
                        messages.addAll(A2uiMessages.review(p));
                    }
                    return data.result(Map.of("a2ui", messages, "writesExecuted", 0), "simulated-toolkit-review");
                })
        ), event -> { synchronized (auditLog) { if (auditLog.size() >= 200) auditLog.remove(0); auditLog.add(event); } });
    }
    private static Operations.Binding bind(String id, String description, Map<String,String> inputs,
            boolean mcp, boolean a2a, String app, Backend backend) {
        var definition = new ToolDefinition(id, description, inputs, new ToolDefinition.McpExposure(mcp),
            new ToolDefinition.A2aExposure(a2a, id, description, "0.1.0"),
            new ToolDefinition.A2uiExposure(id.equals("suggest-inventory-transfers"), "inventory-review"),
            new ToolDefinition.McpAppExposure(app != null, app == null ? "ui://unused" : app));
        return new Operations.Binding(definition, (args, ctx) -> backend.call(id, args, ctx));
    }
    @RestController static class Diagnostics {
        private final SimulatorData data; private final List<Operations.Audit> audit;
        Diagnostics(SimulatorData data, List<Operations.Audit> auditLog) { this.data = data; this.audit = auditLog; }
        @GetMapping("/simulator/diagnostics") Map<String,Object> diagnostics() {
            synchronized (audit) { return Map.of("simulated", true, "writes", data.writes(), "audit", List.copyOf(audit)); }
        }
    }
}
