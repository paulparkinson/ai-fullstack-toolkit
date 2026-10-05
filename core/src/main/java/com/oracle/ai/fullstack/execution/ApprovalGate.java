package com.oracle.ai.fullstack.execution;

import java.time.*;
import java.util.*;

/** Single-process reference approval store. Never expose its writer as an ordinary model tool. */
public final class ApprovalGate {
    public record Proposal(String id, String actor, Instant expiresAt, String description,
                           String operation, Map<String, Object> arguments) {}
    private final Map<String, Proposal> pending = new HashMap<>();
    private final Backend writer;
    private final Clock clock;
    private final Duration ttl;
    public ApprovalGate(Backend writer, Clock clock, Duration ttl) {
        this.writer = Objects.requireNonNull(writer); this.clock = Objects.requireNonNull(clock); this.ttl = ttl;
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Approval TTL must be positive");
    }
    public synchronized Proposal propose(String description, String operation, Map<String, Object> args, Operations.Context ctx) {
        pending.values().removeIf(p -> !p.expiresAt().isAfter(clock.instant()));
        if (pending.size() >= 1000) throw new IllegalStateException("Approval queue full");
        // Freeze scalars only: no mutable nested values or client-replaced transfer parameters.
        if (args.values().stream().anyMatch(v -> !(v instanceof String || v instanceof Number || v instanceof Boolean)))
            throw new IllegalArgumentException("Approval arguments must be scalars");
        var p = new Proposal(UUID.randomUUID().toString(), ctx.actor(), clock.instant().plus(ttl), description,
                operation, Map.copyOf(args));
        pending.put(p.id(), p); return p;
    }
    public Operations.Result approve(String id, Operations.Context ctx) {
        Proposal proposal;
        synchronized (this) {
            proposal = pending.get(id);
            if (proposal == null || !proposal.actor().equals(ctx.actor()) || !proposal.expiresAt().isAfter(clock.instant()))
                throw new IllegalArgumentException("Approval missing, expired, consumed, or belongs to another caller");
            pending.remove(id); // Consume BEFORE dispatch; ambiguous backend failures must not trigger a second write.
        }
        return writer.call(proposal.operation(), proposal.arguments(), ctx);
    }
}
