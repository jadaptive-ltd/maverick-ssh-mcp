package com.jadaptive.mcp;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

enum GatedTool {
    SSHTEAM_REGISTER("sshteam_register"),
    SSHTEAM_STATUS("sshteam_status"),
    SSHTEAM_REGISTER_POLL("sshteam_register_poll"),
    SSHTEAM_REVOKE_DEVICE("sshteam_revoke_device"),
    SSH_CONNECT("ssh_connect"),
    SSH_STATUS("ssh_status"),
    SSH_CLOSE("ssh_close"),
    SOCKET_OPEN("socket_open"),
    SOCKET_STATUS("socket_status"),
    SOCKET_SET_OPTIONS("socket_set_options"),
    SOCKET_WRITE("socket_write"),
    SOCKET_READ("socket_read"),
    SOCKET_CLOSE("socket_close"),
    SOCKET_LISTEN_OPEN("socket_listen_open"),
    SOCKET_LISTEN_ACCEPT("socket_listen_accept"),
    SOCKET_LISTEN_CLOSE("socket_listen_close"),
    SHELL_OPEN("shell_open"),
    SHELL_WRITE("shell_write"),
    SHELL_READ("shell_read"),
    SHELL_CLOSE("shell_close"),
    EXPECT_SHELL_OPEN("expectShellOpen"),
    EXPECT_SHELL_CLOSE("expectShellClose"),
    EXPECT_SHELL_GET_CHARACTER_ENCODING("expectShellGetCharacterEncoding"),
    EXPECT_SHELL_SET_CHARACTER_ENCODING("expectShellSetCharacterEncoding"),
    EXPECT_SHELL_SET_SHELL_INIT_TIMEOUT("expectShellSetShellInitTimeout"),
    EXPECT_SHELL_IN_STARTUP("expectShellInStartup"),
    EXPECT_SHELL_SU("expectShellSu"),
    EXPECT_SHELL_SUDO("expectShellSudo"),
    EXPECT_SHELL_IS_CLOSED("expectShellIsClosed"),
    EXPECT_SHELL_EXIT("expectShellExit"),
    EXPECT_SHELL_GET_NEWLINE("expectShellGetNewline"),
    EXPECT_SHELL_EXECUTE_WITH_OUTPUT("expectShellExecuteWithOutput"),
    EXPECT_SHELL_EXECUTE_WITH_EXIT_CODE("expectShellExecuteWithExitCode"),
    EXPECT_SHELL_EXECUTE("expectShellExecute"),
    EXPECT_SHELL_EXECUTE_COMMAND("expectShellExecuteCommand"),
    EXPECT_SHELL_GET_NUM_COMMANDS_EXECUTED("expectShellGetNumCommandsExecuted"),
    EXPECT_SHELL_GET_OS_TYPE("expectShellGetOsType"),
    EXPECT_SHELL_GET_OS_DESCRIPTION("expectShellGetOsDescription"),
    SFTP_OPEN("sftp_open"),
    SFTP_CLOSE("sftp_close"),
    SFTP_PWD("sftp_pwd"),
    SFTP_CD("sftp_cd"),
    SFTP_LS("sftp_ls"),
    SFTP_STAT("sftp_stat"),
    SFTP_MKDIR("sftp_mkdir"),
    SFTP_RMDIR("sftp_rmdir", true),
    SFTP_RM("sftp_rm", true),
    SFTP_RENAME("sftp_rename"),
    SFTP_SYMLINK("sftp_symlink"),
    SFTP_CHMOD("sftp_chmod"),
    SFTP_CHOWN("sftp_chown"),
    SFTP_FILE_OPEN("sftp_file_open"),
    SFTP_FILE_READ("sftp_file_read"),
    SFTP_FILE_WRITE("sftp_file_write"),
    SFTP_FILE_CLOSE("sftp_file_close"),
    TUNNEL_OPEN_LOCAL("tunnel_open_local"),
    TUNNEL_OPEN_REMOTE("tunnel_open_remote"),
    TUNNEL_CLOSE("tunnel_close"),
    SCP_COPY_TO("scp_copy_to"),
    SCP_COPY_FROM("scp_copy_from");

    private static final Map<String, GatedTool> BY_TOOL_NAME = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(GatedTool::toolName, Function.identity()));

    private final String toolName;
    private final boolean deleteOperation;

    GatedTool(String toolName) {
        this(toolName, false);
    }

    GatedTool(String toolName, boolean deleteOperation) {
        this.toolName = toolName;
        this.deleteOperation = deleteOperation;
    }

    String toolName() {
        return toolName;
    }

    boolean isDeleteOperation() {
        return deleteOperation;
    }

    static Optional<GatedTool> fromToolName(String toolName) {
        return Optional.ofNullable(BY_TOOL_NAME.get(toolName));
    }
}
