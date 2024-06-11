/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.launcher.daemon.diagnostics;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

/**
 * Contains some daemon diagnostics information useful for the client.
 */
public class DaemonDiagnostics {

    @Nullable
    private final Long pid;
    private final File daemonLog;
    private final static int TAIL_SIZE = 20;

    public DaemonDiagnostics(File daemonLog, @Nullable Long pid) {
        this.daemonLog = daemonLog;
        this.pid = pid;
    }

    /**
     * @return pid. Can be null, it means the daemon was not able to identify its pid.
     */
    public @Nullable Long getPid() {
        return pid;
    }

    public File getDaemonLog() {
        return daemonLog;
    }

    @Override
    public String toString() {
        return "{"
            + "pid=" + pid
            + ", daemonLog=" + daemonLog
            + '}';
    }

    private String tailDaemonLog() {
        try {
            String tail = DaemonLogFileUtils.tail(getDaemonLog(), TAIL_SIZE);
            return formatTail(tail);
        } catch (IOException e) {
            return "Unable to read from the daemon log file: " + getDaemonLog().getAbsolutePath() + ", because of: " + e.getCause();
        }
    }

    private String formatTail(String tail) {
        return "----- Last  " + TAIL_SIZE + " lines from daemon log file - " + getDaemonLog().getName() + " -----\n"
            + tail
            + "----- End of the daemon log -----\n";
    }

    public String describe() {
        return "Daemon pid: " + pid + "\n"
            + "  log file: " + daemonLog + "\n"
            + tailDaemonLog();
    }
}
