package com.oracle.ai.fullstack.projection;

import com.oracle.ai.fullstack.execution.ApprovalGate;
import java.util.*;

/** A2UI v0.8 standard-catalog subset (Column, Text, Button). */
public final class A2uiMessages {
    public static final String EXTENSION = "https://a2ui.org/a2a-extension/a2ui/v0.8";
    private A2uiMessages() {}
    public static List<Map<String,Object>> review(ApprovalGate.Proposal proposal) {
        String surface = "review-" + proposal.id();
        return List.of(
            Map.of("surfaceUpdate", Map.of("surfaceId", surface, "components", List.of(
                Map.of("id", "root", "component", Map.of("Column", Map.of("children", Map.of("explicitList", List.of("summary", "approve"))))),
                Map.of("id", "summary", "component", Map.of("Text", Map.of("text", Map.of("literalString", proposal.description())))),
                Map.of("id", "label", "component", Map.of("Text", Map.of("text", Map.of("literalString", "Approve this transfer")))),
                Map.of("id", "approve", "component", Map.of("Button", Map.of("child", "label", "action", Map.of("name", "approve-transfer", "context", List.of(
                    Map.of("key", "proposalId", "value", Map.of("literalString", proposal.id())))))))))),
            Map.of("beginRendering", Map.of("surfaceId", surface, "root", "root")));
    }
}
