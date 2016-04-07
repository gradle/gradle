package org.gradle.launcher.daemon.configuration;

public class DaemonExpirationStrategy {
    static final int DEFAULT_IDLE_TIMEOUT_MS = 3 * 60 * 60 * 1000;
    static final int DEFAULT_PERIODIC_CHECK_MS = 60 * 1000;
    private int idleTimeout = DEFAULT_IDLE_TIMEOUT_MS;
    private int periodicRegistryCheckMs = DEFAULT_PERIODIC_CHECK_MS;
}
