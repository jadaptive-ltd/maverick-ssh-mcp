package com.jadaptive.mcp;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Enforces the persistent-shell command boundary (see the Command Intelligence
 * {@code docs/persistent-shell-boundary.md} design record) before a tool call is verified by a gate
 * that authorises individual commands.
 * <p>
 * The checks are structural only: they stop one assessed string from becoming several shell inputs,
 * stop raw input to unmediated interactive shells, and stop commands from carrying the ExpectShell
 * completion markers. Semantic assessment remains the gate's responsibility.
 */
final class CommandBoundary {

    /** ExpectShell marker literals; a command must never contain these. */
    static final Set<String> MARKER_LITERALS = Set.of("---BEGIN---", "---END---");

    private static final Set<String> EXPECT_COMMAND_TOOLS = Set.of(
            "expect_execute", "expect_execute_with_output", "expect_execute_with_exit_code",
            "expect_execute_command", "expect_su", "expect_sudo");

    private static final String SUDO_OPTS = "(?:\\s+-[a-zA-Z]*[uUgCDhpRrT]\\s*[^\\s-]\\S*|\\s+-[a-zA-Z]+|\\s+--\\S+)*";
    private static final String SEGMENT = "(?:^|[;&|(`]|\\$\\()\\s*";
    private static final String NO_COMMAND = "(?![^;&|]*\\s(?:-c|--command|--session-command)\\b)";

    /**
     * Interactive identity switches. These change the identity of a persistent shell and are only permitted
     * through {@code expect_su}, which records the hop in the session's privilege chain.
     */
    static final java.util.regex.Pattern IDENTITY_SWITCH = java.util.regex.Pattern.compile("(?m)"
            + SEGMENT + "su\\b" + NO_COMMAND
            + "|" + SEGMENT + "sudo" + SUDO_OPTS + "\\s+-[a-zA-Z]*[si][a-zA-Z]*\\s*(?:$|[;&|)`])"
            + "|" + SEGMENT + "sudo" + SUDO_OPTS + "\\s+su\\b" + NO_COMMAND
            + "|" + SEGMENT + "doas\\s+(?:-\\S+\\s+)*-s\\b");

    private CommandBoundary() {
    }

    /**
     * Checks a tool call against the boundary.
     *
     * @return a refusal reason, or empty when the call is within the boundary
     */
    static Optional<String> check(String toolName, Map<String, Object> args, HandleRegistry registry) {
        Optional<String> violation = checkStructure(toolName, args, registry);
        if (violation.isPresent() || "expect_su".equals(toolName)) {
            return violation;
        }
        String command = commandOf(toolName, args);
        if (command != null && IDENTITY_SWITCH.matcher(command).find()) {
            return Optional.of(toolName + " cannot switch user identity while command mediation is active; use "
                    + "expect_su so the identity change is tracked.");
        }
        return Optional.empty();
    }

    private static String commandOf(String toolName, Map<String, Object> args) {
        if (StandardGatedTool.SSH_EXEC.toolName().equals(toolName) || "shell_open".equals(toolName)) {
            return stringValue(args, "command");
        }
        if ("shell_write".equals(toolName)) {
            return stringValue(args, "data");
        }
        return EXPECT_COMMAND_TOOLS.contains(toolName) ? stringValue(args, "cmd") : null;
    }

    private static Optional<String> checkStructure(String toolName, Map<String, Object> args,
            HandleRegistry registry) {
        if (StandardGatedTool.SSH_EXEC.toolName().equals(toolName)) {
            return checkCommand(toolName, stringValue(args, "command"), true);
        }
        if ("shell_open".equals(toolName)) {
            String command = stringValue(args, "command");
            // An interactive shell may be opened, but only for use through expect_open (see shell_write).
            return command == null || command.isBlank() ? Optional.empty() : checkCommand(toolName, command, true);
        }
        if ("shell_write".equals(toolName)) {
            String shellHandle = stringValue(args, "shellHandle");
            if (registry.isInteractiveShell(shellHandle)) {
                return Optional.of("shell_write to an interactive shell is not permitted while command mediation is "
                        + "active; open an ExpectShell with expect_open and use the expect_execute tools.");
            }
            if (args.get("appendNewline") instanceof Boolean append && !append) {
                return Optional.of("shell_write requires appendNewline=true while command mediation is active.");
            }
            return checkCommand(toolName, stringValue(args, "data"), false);
        }
        if (EXPECT_COMMAND_TOOLS.contains(toolName)) {
            return checkCommand(toolName, stringValue(args, "cmd"), false);
        }
        return Optional.empty();
    }

    /**
     * Checks a single command string.
     *
     * @param allowLineFeed whether LF may appear (stateless exec channels only)
     */
    static Optional<String> checkCommand(String toolName, String command, boolean allowLineFeed) {
        if (command == null) {
            return Optional.empty();
        }
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (ch == '\t') {
                continue;
            }
            if (ch == '\n' && allowLineFeed) {
                continue;
            }
            if (ch == '\n' || ch == '\r') {
                return Optional.of(toolName + " command must be a single line while command mediation is active.");
            }
            if (Character.isISOControl(ch)) {
                return Optional.of(toolName + " command contains a control character (U+"
                        + String.format("%04X", (int) ch) + ") and is not permitted.");
            }
        }
        for (String marker : MARKER_LITERALS) {
            if (command.contains(marker)) {
                return Optional.of(toolName + " command contains a reserved shell completion marker and is not "
                        + "permitted.");
            }
        }
        return Optional.empty();
    }

    private static String stringValue(Map<String, Object> args, String name) {
        return args.get(name) instanceof String value ? value : null;
    }
}
