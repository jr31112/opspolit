package com.example.opspilot.web;

public enum BlockedApiPath {
    MONITOR("/monitor");

    private final String path;

    BlockedApiPath(String path) {
        this.path = path;
    }

    public boolean matches(String requestPath) {
        return path.equals(requestPath) || requestPath.startsWith(path + "/");
    }

    public static boolean isBlocked(String requestPath) {
        for (BlockedApiPath blockedPath : values()) {
            if (blockedPath.matches(requestPath)) {
                return true;
            }
        }
        return false;
    }
}
