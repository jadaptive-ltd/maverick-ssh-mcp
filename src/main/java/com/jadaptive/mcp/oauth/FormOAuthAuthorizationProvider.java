package com.jadaptive.mcp.oauth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Basic in-memory login form that authenticates with the configured MCP bearer token. */
public final class FormOAuthAuthorizationProvider implements OAuthAuthorizationProvider {

    private final byte[] expectedPassword;

    public FormOAuthAuthorizationProvider(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("OAuth form password cannot be blank");
        }
        this.expectedPassword = password.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void beginAuthorization(
            OAuthAuthorizationRequest authorization,
            OAuthAuthorizationFlow flow,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        writeForm(flow, response, null);
    }

    @Override
    public void continueAuthorization(
            OAuthAuthorizationFlow flow,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if ("reject".equals(request.getParameter("decision"))) {
            flow.deny("The user rejected the authorization request");
            return;
        }

        String password = request.getParameter("password");
        if (password == null || !MessageDigest.isEqual(
                expectedPassword,
                password.getBytes(StandardCharsets.UTF_8))) {
            writeForm(flow, response, "The password is incorrect.");
            return;
        }

        if (!"allow".equals(request.getParameter("decision"))) {
            writeForm(flow, response, null);
            return;
        }
        flow.approve("mcp-user");
    }

    private static void writeForm(
            OAuthAuthorizationFlow flow,
            HttpServletResponse response,
            String error) throws IOException {
        OAuthAuthorizationRequest authorization = flow.authorizationRequest();
        String client = authorization.clientName() == null || authorization.clientName().isBlank()
                ? authorization.clientId()
                : authorization.clientName();
        String scopes = authorization.scopes().isEmpty()
                ? "MCP server access"
                : String.join(", ", authorization.scopes());
        String errorMarkup = error == null ? "" : "<p role=\"alert\" class=\"error\">" + escape(error) + "</p>";

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
        response.getWriter().write("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>Authorize MCP client</title>
                  <style>
                    :root { color-scheme: light dark; font-family: system-ui, sans-serif; }
                    body { display:grid; min-height:100vh; margin:0; place-items:center; background:#111827; color:#f9fafb; }
                    main { width:min(26rem,calc(100vw - 3rem)); border:1px solid #4b5563; border-radius:.75rem; padding:1.5rem; background:#1f2937; }
                    h1 { font-size:1.4rem; margin-top:0; }
                    label { display:block; margin:.75rem 0 .3rem; }
                    input { box-sizing:border-box; width:100%%; padding:.65rem; border:1px solid #6b7280; border-radius:.35rem; }
                    .actions { display:flex; gap:.75rem; margin-top:1rem; }
                    button { flex:1; padding:.65rem; border:0; border-radius:.35rem; cursor:pointer; font-weight:600; }
                    .allow { background:#2563eb; color:white; }
                    .reject { background:#374151; color:white; }
                    .error { color:#fca5a5; }
                    .detail { color:#d1d5db; }
                  </style>
                </head>
                <body>
                  <main>
                    <h1>Authorize MCP client</h1>
                    <p><strong>%s</strong> is requesting access to this Maverick SSH MCP server.</p>
                    <p class="detail">Requested access: %s</p>
                    %s
                    <form method="post" action="%s">
                      <label for="password">MCP token</label>
                      <input id="password" name="password" type="password" autocomplete="current-password" required autofocus>
                      <div class="actions">
                        <button class="reject" name="decision" value="reject" formnovalidate type="submit">Reject</button>
                        <button class="allow" name="decision" value="allow" type="submit">Allow</button>
                      </div>
                    </form>
                  </main>
                </body>
                </html>
                """.formatted(escape(client), escape(scopes), errorMarkup, escape(flow.callbackUri().toString())));
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}