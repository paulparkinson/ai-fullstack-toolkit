package com.oracle.ai.fullstack.starter;

import jakarta.servlet.http.HttpServletRequest;

/** Resolve only authenticated server-side identity, never an actor supplied in tool arguments. */
@FunctionalInterface
public interface CallerResolver {
    String actor(HttpServletRequest request);
}
