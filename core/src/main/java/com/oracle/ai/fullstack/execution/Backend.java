package com.oracle.ai.fullstack.execution;

import java.util.Map;

/** Application-owned boundary: managed-agent reads and toolkit writes use separate instances. */
@FunctionalInterface
public interface Backend {
    Operations.Result call(String operation, Map<String, Object> arguments, Operations.Context context);
}
