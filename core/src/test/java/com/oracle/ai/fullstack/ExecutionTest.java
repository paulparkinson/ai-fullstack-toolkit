package com.oracle.ai.fullstack;

import com.oracle.ai.fullstack.execution.*;
import com.oracle.ai.fullstack.model.ToolDefinition;
import com.oracle.ai.fullstack.projection.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionTest {
    final Operations.Context alice = new Operations.Context("alice", "request-1");
    final Operations.Context bob = new Operations.Context("bob", "request-2");
    Operations.Result result() { return new Operations.Result(Map.of("ok", true), new Operations.Evidence("test", true, "test-1")); }
    ToolDefinition definition() { return new ToolDefinition("read", "Read", Map.of("sku", "string"),
        new ToolDefinition.McpExposure(true), new ToolDefinition.A2aExposure(false, "read", "Read", "1"), null, null); }
    @Test void validatesInputsAndExposureBeforeCallingBackend() {
        var calls = new AtomicInteger(); var audit = new ArrayList<Operations.Audit>();
        var ops = new Operations(List.of(new Operations.Binding(definition(), (a,c) -> { calls.incrementAndGet(); return result(); })), audit::add);
        assertThrows(IllegalArgumentException.class, () -> ops.execute("read", Map.of("sku", "SKU-500"), Operations.Surface.A2A, alice));
        assertThrows(IllegalArgumentException.class, () -> ops.execute("read", Map.of("sku", "SKU-500", "evidence", "forged"), Operations.Surface.MCP, alice));
        assertThrows(IllegalArgumentException.class, () -> ops.execute("read", Map.of("sku", 42), Operations.Surface.MCP, alice));
        assertThrows(IllegalArgumentException.class, () -> ops.execute("unknown", Map.of(), Operations.Surface.MCP, alice));
        assertEquals(0, calls.get());
        assertTrue(ops.execute("read", Map.of("sku", "SKU-500"), Operations.Surface.MCP, alice).evidence().simulated());
        assertEquals(1, calls.get()); assertEquals("alice", audit.get(0).actor());
    }
    @Test void backendFailureNeverReturnsFixtureFallback() {
        var audit = new ArrayList<Operations.Audit>();
        var ops = new Operations(List.of(new Operations.Binding(definition(), (a,c) -> { throw new IllegalStateException("offline"); })), audit::add);
        assertThrows(IllegalStateException.class, () -> ops.execute("read", Map.of("sku", "SKU-500"), Operations.Surface.MCP, alice));
        assertFalse(audit.get(0).succeeded()); assertNull(audit.get(0).evidence());
    }
    @Test void duplicateBindingsFailAtStartup() {
        var b = new Operations.Binding(definition(), (a,c) -> result());
        assertThrows(IllegalArgumentException.class, () -> new Operations(List.of(b,b), a -> {}));
    }
    @Test void approvalIsBoundToCallerAndImmutableArgumentsAndSingleUse() {
        var calls = new AtomicInteger(); var args = new HashMap<String,Object>(Map.of("quantity", 20));
        var gate = new ApprovalGate((op,a,c) -> { assertEquals(20, a.get("quantity")); calls.incrementAndGet(); return result(); }, Clock.systemUTC(), Duration.ofMinutes(5));
        var proposal = gate.propose("Move 20", "transfer", args, alice); args.put("quantity", 900);
        assertEquals(0, calls.get());
        assertThrows(IllegalArgumentException.class, () -> gate.approve(proposal.id(), bob));
        gate.approve(proposal.id(), alice);
        assertThrows(IllegalArgumentException.class, () -> gate.approve(proposal.id(), alice));
        assertEquals(1, calls.get());
    }
    @Test void approvalExpiresAndCannotRetryAfterAmbiguousFailure() {
        var now = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() { public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;} public Instant instant(){return now.get();} };
        var gate = new ApprovalGate((op,a,c) -> { throw new IllegalStateException("timeout after write"); }, clock, Duration.ofSeconds(1));
        var expired = gate.propose("Move", "transfer", Map.of(), alice);
        now.set(now.get().plusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> gate.approve(expired.id(), alice));
        var p = gate.propose("Move", "transfer", Map.of(), alice);
        assertThrows(IllegalStateException.class, () -> gate.approve(p.id(), alice));
        assertThrows(IllegalArgumentException.class, () -> gate.approve(p.id(), alice));
    }
    @Test void schemasAndA2uiUseProtocolShapes() {
        var schema = (Map<?,?>)ToolProjections.mcpDescriptor(definition()).get("inputSchema");
        assertEquals(Map.of("sku", Map.of("type", "string")), schema.get("properties"));
        assertEquals(false, schema.get("additionalProperties"));
        var p = new ApprovalGate.Proposal("123", "alice", Instant.now(), "Review", "write", Map.of());
        var ui = A2uiMessages.review(p);
        assertTrue(ui.get(0).containsKey("surfaceUpdate")); assertTrue(ui.get(1).containsKey("beginRendering"));
    }
}
