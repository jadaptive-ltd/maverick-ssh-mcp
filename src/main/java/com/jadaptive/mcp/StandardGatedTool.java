package com.jadaptive.mcp;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum StandardGatedTool implements GatedTool {
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
    EXPECT_SHELL_OPEN("expect_open"),
    EXPECT_SHELL_CLOSE("expect_close"),
    EXPECT_SHELL_GET_CHARACTER_ENCODING("expect_get_character_encoding"),
    EXPECT_SHELL_SET_CHARACTER_ENCODING("expect_set_character_encoding"),
    EXPECT_SHELL_SET_SHELL_INIT_TIMEOUT("expect_set_shell_init_timeout"),
    EXPECT_SHELL_IN_STARTUP("expect_in_startup"),
    EXPECT_SHELL_SU("expect_su"),
    EXPECT_SHELL_SUDO("expect_sudo"),
    EXPECT_SHELL_IS_CLOSED("expect_is_closed"),
    EXPECT_SHELL_EXIT("expect_exit"),
    EXPECT_SHELL_GET_NEWLINE("expect_get_newline"),
    EXPECT_SHELL_EXECUTE_WITH_OUTPUT("expect_execute_with_output"),
    EXPECT_SHELL_EXECUTE_WITH_EXIT_CODE("expect_execute_with_exit_code"),
    EXPECT_SHELL_EXECUTE("expect_execute"),
    EXPECT_SHELL_EXECUTE_COMMAND("expect_execute_command"),
    EXPECT_SHELL_GET_NUM_COMMANDS_EXECUTED("expect_get_num_commands_executed"),
    EXPECT_SHELL_GET_OS_TYPE("expect_get_os_type"),
    EXPECT_SHELL_GET_OS_DESCRIPTION("expect_get_os_description"),
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
    SCP_COPY_FROM("scp_copy_from"),
    SSH_EXEC("ssh_exec");

    private static final Map<String, StandardGatedTool> BY_TOOL_NAME = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(StandardGatedTool::toolName, Function.identity()));

    private final String toolName;
    private final boolean deleteOperation;

    StandardGatedTool(String toolName) {
        this(toolName, false);
    }

    StandardGatedTool(String toolName, boolean deleteOperation) {
        this.toolName = toolName;
        this.deleteOperation = deleteOperation;
    }

    @Override	
    public String toolName() {
        return toolName;
    }

    @Override
    public boolean isDeleteOperation() {
        return deleteOperation;
    }

    static Optional<StandardGatedTool> fromToolName(String toolName) {
        return Optional.ofNullable(BY_TOOL_NAME.get(toolName));
    }
}