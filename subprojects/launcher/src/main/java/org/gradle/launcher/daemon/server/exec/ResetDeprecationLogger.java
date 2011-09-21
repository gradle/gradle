package org.gradle.launcher.daemon.server.exec;

import org.gradle.util.DeprecationLogger;

public class ResetDeprecationLogger implements DaemonCommandAction {
    public void execute(DaemonCommandExecution execution) {
        DeprecationLogger.reset();
        execution.proceed();
    }
}
