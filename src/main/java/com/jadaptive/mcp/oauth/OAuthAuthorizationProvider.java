package com.jadaptive.mcp.oauth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentication UI/flow provider for the MCP OAuth authorization endpoint.
 * Implementations control how a user is authenticated and approved, while the
 * runtime owns client registration, PKCE, authorization codes and access tokens.
 */
public interface OAuthAuthorizationProvider extends AutoCloseable {

    /** Starts an interactive authorization flow. */
    void beginAuthorization(
            OAuthAuthorizationRequest authorization,
            OAuthAuthorizationFlow flow,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException;

    /**
     * Resolves a provider callback to its runtime transaction. The default form
     * flow uses the explicit transaction_id generated in the callback URI.
     * External providers can override this to extract their own state value.
     */
    default String transactionId(HttpServletRequest request) {
        return request.getParameter("transaction_id");
    }

    /**
     * Continues a flow after redirecting away from the authorization endpoint.
     * The callback URI exposed by {@link OAuthAuthorizationFlow} contains the
     * transaction identifier so any external identity provider state can refer
     * back to the runtime-managed authorization transaction.
     */
    default void continueAuthorization(
            OAuthAuthorizationFlow flow,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    default void close() {
    }
}