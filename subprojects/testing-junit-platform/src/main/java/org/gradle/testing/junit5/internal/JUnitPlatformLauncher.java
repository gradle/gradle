package org.gradle.testing.junit5.internal;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherFactory;

public class JUnitPlatformLauncher implements Runnable {
    @Override
    public void run() {
        Launcher launcher = LauncherFactory.create();
    }
}
