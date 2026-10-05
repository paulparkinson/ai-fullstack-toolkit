package com.oracle.ai.fullstack.starter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class CallerSecurityTest {
    @Test void defaultResolverRequiresAuthenticatedPrincipalAndIgnoresActorClaims() {
        var resolver = new ProtocolAutoConfiguration().callerResolver();
        var request = new MockHttpServletRequest();
        request.addHeader("X-Actor", "admin"); request.addParameter("actor", "admin");
        assertThrows(IllegalArgumentException.class, () -> resolver.actor(request));
        request.setUserPrincipal(() -> "verified-user");
        assertEquals("verified-user", resolver.actor(request));
    }
}
