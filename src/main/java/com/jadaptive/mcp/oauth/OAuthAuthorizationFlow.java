package com.jadaptive.mcp.oauth;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Completion handle for a pending authorization. Provider implementations can
 * redirect elsewhere and later resume the flow through the callback URI.
 */
public interface OAuthAuthorizationFlow {

    OAuthAuthorizationRequest authorizationRequest();

    String transactionId();

    URI callbackUri();

    void approve(String subject) throws IOException;

    default void approve(String subject, Map<String, Object> attributes) throws IOException {
        approve(subject);
    }

    void deny(String description) throws IOException;
}
