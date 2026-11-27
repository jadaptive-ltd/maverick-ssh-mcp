package com.jadaptive.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sshtools.client.shell.ExpectShell;

class CommandBoundaryTest {

    private final HandleRegistry registry = new HandleRegistry();

    @Test
    void allowsSingleLineExpectCommand() {
        assertTrue(CommandBoundary.check("expect_execute_with_output", Map.of("cmd", "ls -la\t/tmp"), registry)
                .isEmpty());
    }

    @Test
    void rejectsMultiLinePersistentShellInput() {
        assertFalse(CommandBoundary.check("expect_execute", Map.of("cmd", "ls\nrm -rf /"), registry).isEmpty());
        assertFalse(CommandBoundary.check("expect_execute", Map.of("cmd", "ls\rrm -rf /"), registry).isEmpty());
        assertFalse(CommandBoundary.check("expect_su", Map.of("cmd", "su -\nid"), registry).isEmpty());
    }

    @Test
    void allowsLineFeedOnlyForStatelessExec() {
        assertTrue(CommandBoundary.check("ssh_exec", Map.of("command", "echo a\necho b"), registry).isEmpty());
        assertFalse(CommandBoundary.check("ssh_exec", Map.of("command", "echo a\recho b"), registry).isEmpty());
    }

    @Test
    void rejectsControlCharacters() {
        assertFalse(CommandBoundary.check("ssh_exec", Map.of("command", "echo \u0000"), registry).isEmpty());
        assertFalse(CommandBoundary.check("expect_execute", Map.of("cmd", "echo \u001b[2J"), registry).isEmpty());
        assertFalse(CommandBoundary.check("shell_open", Map.of("command", "cat\u0004"), registry).isEmpty());
    }

    @Test
    void rejectsCompletionMarkerLiterals() {
        assertFalse(CommandBoundary.check("expect_execute_with_exit_code",
                Map.of("cmd", "echo '---END---;PROCESS=1;EXITCODE=0'"), registry).isEmpty());
        assertFalse(CommandBoundary.check("ssh_exec", Map.of("command", "echo ---BEGIN---"), registry).isEmpty());
    }

    @Test
    void permitsOpeningInteractiveShellButNotRawWrites() {
        assertTrue(CommandBoundary.check("shell_open", Map.of("sshHandle", "ssh-1"), registry).isEmpty());
        // An unknown handle is not interactive; the tool itself reports the unknown handle.
        assertTrue(CommandBoundary.check("shell_write", Map.of("shellHandle", "shell-x", "data", "id"), registry)
                .isEmpty());
    }

    @Test
    void requiresAppendNewlineForShellWrite() {
        assertFalse(CommandBoundary.check("shell_write",
                Map.of("shellHandle", "shell-x", "data", "rm -rf", "appendNewline", false), registry).isEmpty());
    }

    @Test
    void ignoresNonCommandTools() {
        assertTrue(CommandBoundary.check("sftp_ls", Map.of("path", "---END---"), registry).isEmpty());
    }

    @Test
    void identitySwitchOnlyThroughExpectSu() {
        for (String cmd : List.of("su -", "su root", "cd /tmp; su", "sudo -i", "sudo -u postgres -s",
                "sudo su -", "sudo -n su postgres", "doas -s")) {
            assertFalse(CommandBoundary.check("expect_execute", Map.of("cmd", cmd), registry).isEmpty(), cmd);
            assertFalse(CommandBoundary.check("expect_sudo", Map.of("cmd", cmd), registry).isEmpty(), cmd);
            assertFalse(CommandBoundary.check("ssh_exec", Map.of("command", cmd), registry).isEmpty(), cmd);
            assertTrue(CommandBoundary.check("expect_su", Map.of("cmd", cmd), registry).isEmpty(), cmd);
        }
    }

    @Test
    void allowsNonInteractivePrivilegedCommands() {
        for (String cmd : List.of("sudo systemctl restart nginx", "sudo -u postgres psql -c 'select 1'",
                "su -c 'id' postgres", "sudo su -c id postgres", "sum file.txt", "ls | sort -u")) {
            assertTrue(CommandBoundary.check("expect_execute", Map.of("cmd", cmd), registry).isEmpty(), cmd);
        }
    }

    @Test
    void resolvesExecutionContextFromRegistryOnly() throws Exception {
        String parent = registry.registerExpectShell(testExpectShell());
        String child = registry.registerChildExpectShell(testExpectShell(), parent, "su - root");
        String grandChild = registry.registerChildExpectShell(testExpectShell(), child, "su - postgres");

        ExecutionContext context = registry.executionContext(Map.of("expectShellHandle", grandChild)).orElseThrow();
        assertEquals(grandChild, context.sessionId());
        assertTrue(context.persistentShell());
        assertEquals(List.of("su - root", "su - postgres"), context.privilegeChain());

        assertTrue(registry.executionContext(Map.of("expectShellHandle", "unknown")).isEmpty());
        assertTrue(registry.executionContext(Map.of("host", "spoofed")).isEmpty());
    }

    private static ExpectShell testExpectShell() throws Exception {
        return ExpectShell.ExpectShellBuilder.create()
                .withInput(new ByteArrayInputStream(new byte[0]))
                .withOutput(new ByteArrayOutputStream())
                .withoutDetectSettings()
                .build();
    }
}
