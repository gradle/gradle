package org.gradle.testing.junit5.internal;

import org.gradle.testing.junit5.JUnitPlatformOptions;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherFactory;

import javax.inject.Inject;

public class JUnitPlatformLauncher implements Runnable {
    private final JUnitPlatformOptions options;
    private final int port;

    @Inject
    public JUnitPlatformLauncher(JUnitPlatformOptions options, int port) {
        this.options = options;
        this.port = port;
    }

    @Override
    public void run() {
        Launcher launcher = LauncherFactory.create();
    }
}
