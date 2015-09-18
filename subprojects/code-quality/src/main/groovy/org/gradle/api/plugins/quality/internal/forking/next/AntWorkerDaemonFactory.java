package org.gradle.api.plugins.quality.internal.forking.next;

import java.io.File;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;


public interface AntWorkerDaemonFactory {
    // TODO - workingDir should be injected into the implementation
    AntWorkerDaemon getDaemon(File workingDir, DaemonForkOptions forkOptions);
}
