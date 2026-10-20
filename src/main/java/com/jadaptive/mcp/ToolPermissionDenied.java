package com.jadaptive.mcp;

final class ToolPermissionDenied extends Exception {

    private static final long serialVersionUID = 1L;

    ToolPermissionDenied(String message) {
        super(message);
    }
}
