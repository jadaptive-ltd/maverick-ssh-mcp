package com.jadaptive.mcp;

import java.io.IOException;

import com.sshtools.client.PublicKeyAuthenticator;
import com.sshtools.common.publickey.SshKeyPairGenerator;
import com.sshtools.common.publickey.SshKeyUtils;
import com.sshtools.common.ssh.SshException;
import com.sshtools.common.ssh.components.SshKeyPair;
import com.sshtools.common.ssh.components.SshPublicKey;
import com.sshtools.synergy.ssh.Connection;

final class SshTeamCertificateAuthenticator extends PublicKeyAuthenticator {

    private final SshTeamService sshTeamService;
    private final String preferredServerUrl;
    private final boolean ignoreSslTrust;
    private final String certificateType;
    private final String timezone;

    private SshKeyPair keyPair;
    private SshPublicKey certificatePublicKey;
    private boolean used;

    SshTeamCertificateAuthenticator(SshTeamService sshTeamService, String preferredServerUrl, boolean ignoreSslTrust,
            String certificateType, String timezone) {
        this.sshTeamService = sshTeamService;
        this.preferredServerUrl = preferredServerUrl;
        this.ignoreSslTrust = ignoreSslTrust;
        this.certificateType = certificateType;
        this.timezone = timezone;
    }

    @Override
    protected void onStartAuthentication(Connection<com.sshtools.client.SshClientContext> con) {
        used = false;
        certificatePublicKey = null;
        keyPair = null;
        try {
            String effectiveType = normalizeCertificateType(certificateType);
            keyPair = "RSA".equals(effectiveType)
                    ? SshKeyPairGenerator.generateKeyPair(SshKeyPairGenerator.SSH2_RSA, 3072)
                    : SshKeyPairGenerator.generateKeyPair(SshKeyPairGenerator.ED25519);
            String publicKey = SshKeyUtils.getOpenSSHFormattedKey(keyPair.getPublicKey()).trim();
            String serverFingerprint = resolveServerFingerprint(con.getHostKey());
            String certificate = sshTeamService.requestCertificate(
                    preferredServerUrl,
                    ignoreSslTrust,
                    con.getUsername(),
                    serverFingerprint,
                    publicKey,
                    effectiveType,
                    timezone);
            certificatePublicKey = SshKeyUtils.getPublicKey(certificate);
        }
        catch (Exception e) {
            e.printStackTrace();
            certificatePublicKey = null;
            keyPair = null;
        }
    }

    @Override
    protected SshPublicKey getNextKey() throws IOException {
        used = true;
        return certificatePublicKey;
    }

    @Override
    protected SshKeyPair getAuthenticatingKey() throws IOException {
        return keyPair;
    }

    @Override
    protected boolean hasCredentialsRemaining() {
        return !used && certificatePublicKey != null && keyPair != null;
    }

    private static String normalizeCertificateType(String value) {
        if (value == null || value.isBlank()) {
            return "ED25519";
        }
        String normalized = value.trim().toUpperCase();
        if (!"ED25519".equals(normalized) && !"RSA".equals(normalized)) {
            throw new IllegalArgumentException("sshTeamCertificateType must be ED25519 or RSA");
        }
        return normalized;
    }

    private static String resolveServerFingerprint(SshPublicKey hostKey) throws IOException, SshException {
        if (hostKey == null) {
            throw new IllegalStateException("No remote host key available from SSH transport.");
        }
        return SshKeyUtils.getFingerprint(hostKey);
    }
}