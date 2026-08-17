# Maverick MCP Server

Create me a new MCP server project in the project template "maverick-mcp". It is using Maven, namespace com.jadaptive, artifact ID maverick-mcp. We will be using ...

  * Maverick 3.2.0-SNAPSHOT available in workspace at /home/SOUTHPARK/tanktarta/Documents/Git/maverick-synergy-develop-os (but add central snapshots repository for standalone builds)
  * io.modelcontextprotocol.sdk/mcp  (0.11.3 i think is current)
  * picocli - 4.7.6 for command line
  * Java 25 (java level in pom)
  * Graal Native Image
  
User documentation for Synergy at https://docs.jadaptive.com/Maverick%20Synergy/

# Features

We want ..

  * Streamable HTTP support or STDIO  (picocli mode option)
  * Ability to make an SSH client connections and maintain it (close, status etc)
  * Ability to open an SFTP client from there and maintain it (close, status, etc)
  * Ability to open a session channel (e.g shell or other commands) and maintain it (close, status, etc). Allow pty to be configured.
  * Ability to open a tunnel (TCP or Unix Domain Socket) and maintain it (close, status, etc)
  * Ability to use ExpectShell  (a wrapper around a shell to make automated remote commands more reliablee)
  * Full suite of file operations on an open SFTP connection (expose as MCP resources?)
  * Ability to write to and read from session channels and stderr if used (write commands, wait for shell etc)
  * Ability to write to and read from tunnel channels
  * Ability to use legacy SCP file transfers
    
## Connecting

Connecting could involve a few callbacks.

 1. If the host is unknown, or if the signature has changed we must prompt in the type SSH fasion. Show the hostnamee and signature and decide whether to permanently store the answer.
 2. If the authentication requires a password, we need to prompt for that.
 3. If a private key is in use, and that needs a passphrase, we need to prompt for that.
 4. If keyboard interactive is in use, prompt for that
 5. 2-4 can happen more than once

Upon connection, we should have some kind of connection handle that allows subsequent MCP commands to operate.

## Files

Given an SSH connection, opens an SFTP client to an inital path or /. Creates handle use for subsequent SFTP operations. 

 * List directories
 * Get and set file attributes
 * Create directories 
 * Remove files and directories optionally recursively (always prompt?)
 * Open files for read / write etc, produces handle for random read / write (binary), close.
 * Link files
 * Get file system information

## Shells and Commands

Open A Seesson Channel. Given an SSH connections, opens session channel with option (recommended) PTY. Creates handle to allow close, read and write from either stdin, stdout and optionally stderr.  
 
 * Allow PTY settings, have sensible defaults
 * Option to create an ExpectShell (produces new handle for expect shell operations)
 * Default to shell, allow other commands be run

## Tunnels

Open Tunnel. Given an SSH connection, opens a tunnel that can forward local to remote or remote. Creates handle for further operations, close, read, write etc.

 * Allow configuration of either TCP or Unix Domain Socket tunnels
 * Allow local and remote bind host/port or path and local and remote target host/port or path
 * Query active tunnels.

## SCP

Simple either copy from or copy too, optionally recursive of either files or directories.

# Other Tasks

 * Create basic README.md with command line options and example prompts
 * Provide a `native-images` Maven profile that builds the server as a native executable with Graal.
 * Provider a lightweight `Dockerfile` that uses the natively compiled mcp server and starts it in streamable HTTP mode on a host (default  0.0.0.0) and port (default 7693) that are both exposed as environment variables.
