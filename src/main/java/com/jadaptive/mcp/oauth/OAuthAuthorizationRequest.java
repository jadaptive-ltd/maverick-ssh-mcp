package com.jadaptive.mcp.oauth;

import java.net.URI;
import java.util.Set;

/** Immutable details of a validated OAuth authorization request. */
public record OAuthAuthorizationRequest(
        String clientId,
        String clientName,
        URI redirectUri,
        Set<String> scopes,
        String state,
        URI resource) {

    public OAuthAuthorizationRequest {
        scopes = Set.copyOf(scopes);
    }
}
