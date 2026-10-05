package com.oracle.ai.fullstack.execution;

import com.oracle.ai.fullstack.model.ToolDefinition;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Executable, immutable catalog shared by transports. Descriptors alone never grant execution. */
public final class Operations {
    public enum Surface { MCP, A2A }
    public record Context(String actor, String requestId) {
        public Context {
            if (actor == null || actor.isBlank() || requestId == null || requestId.isBlank())
                throw new IllegalArgumentException("Trusted caller and request ID are required");
        }
    }
    public record Evidence(String provider, boolean simulated, String upstreamRequestId) {
        public Evidence {
            if (provider == null || provider.isBlank() || upstreamRequestId == null || upstreamRequestId.isBlank())
                throw new IllegalArgumentException("Backend evidence is required");
        }
    }
    public record Result(Map<String, Object> data, Evidence evidence) {
        public Result { data = Map.copyOf(data); Objects.requireNonNull(evidence); }
    }
    @FunctionalInterface public interface Handler { Result execute(Map<String, Object> arguments, Context context); }
    public record Binding(ToolDefinition definition, Handler handler) {
        public Binding { Objects.requireNonNull(definition); Objects.requireNonNull(handler); }
    }
    public record Audit(String requestId, String actor, String operation, Surface surface,
                        Instant time, boolean succeeded, Evidence evidence) {}
    private final Map<String, Binding> bindings;
    private final Consumer<Audit> audit;

    public Operations(List<Binding> bindings, Consumer<Audit> audit) {
        var map = new LinkedHashMap<String, Binding>();
        for (var b : bindings) {
            if (map.putIfAbsent(b.definition().id(), b) != null) throw new IllegalArgumentException("Duplicate operation");
            for (var type : b.definition().inputSchema().values())
                if (!Set.of("string", "integer", "number", "boolean").contains(type))
                    throw new IllegalArgumentException("Unsupported input type: " + type);
            if (b.definition().mcpApp().enabled() && !b.definition().mcp().enabled())
                throw new IllegalArgumentException("MCP Apps require MCP exposure");
        }
        this.bindings = Collections.unmodifiableMap(map);
        this.audit = Objects.requireNonNull(audit);
    }
    public Collection<Binding> bindings() { return bindings.values(); }
    public Result execute(String id, Map<String, Object> arguments, Surface surface, Context context) {
        var binding = bindings.get(id);
        if (binding == null) throw new IllegalArgumentException("Unknown operation");
        var d = binding.definition();
        if (!(surface == Surface.MCP ? d.mcp().enabled() : d.a2a().enabled()))
            throw new IllegalArgumentException("Operation is not exposed on this surface");
        validate(d, arguments);
        Result result;
        try { result = Objects.requireNonNull(binding.handler().execute(Map.copyOf(arguments), context)); }
        catch (RuntimeException e) {
            audit.accept(new Audit(context.requestId(), context.actor(), id, surface, Instant.now(), false, null));
            throw e;
        }
        audit.accept(new Audit(context.requestId(), context.actor(), id, surface, Instant.now(), true, result.evidence()));
        return result;
    }
    private static void validate(ToolDefinition d, Map<String, Object> arguments) {
        if (arguments == null || !arguments.keySet().equals(d.inputSchema().keySet()))
            throw new IllegalArgumentException("Arguments must exactly match the operation schema");
        d.inputSchema().forEach((key, type) -> {
            Object v = arguments.get(key);
            boolean valid = switch (type) {
                case "string" -> v instanceof String s && !s.isBlank() && s.length() <= 1024;
                case "integer" -> v instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() == Math.rint(n.doubleValue());
                case "number" -> v instanceof Number n && Double.isFinite(n.doubleValue());
                case "boolean" -> v instanceof Boolean;
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("Invalid argument: " + key);
        });
    }
}
