/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.bootstrap;

import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.logging.DaemonMessages;

import java.io.File;
import java.io.PrintStream;

/**
 * by Szczepan Faber, created at: 4/10/12
 */
public class DaemonStartupCommunication {

    private static final String DELIM = ";:"; //this very simple delim should be safe for any kind of path.

    public void printDaemonStarted(PrintStream target, Long pid, File daemonLog) {
        target.println(daemonStartedMessage(pid, daemonLog));
    }

    String daemonStartedMessage(Long pid, File daemonLog) {
        return DaemonMessages.ABOUT_TO_CLOSE_STREAMS + DELIM + pid + DELIM + daemonLog;
    }

    public DaemonDiagnostics readDiagnostics(String message) {
        String[] split = message.split(DELIM);
        String pidString = split[1];
        Long pid = pidString.equals("null")? null : Long.valueOf(pidString);
        File daemonLog = new File(split[2]);
        return new DaemonDiagnostics(daemonLog, pid);
    }
}
