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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.logging.DaemonMessages;

import java.io.File;
import java.io.PrintStream;

public class DaemonStartupCommunication {

    private static final String DELIM = ";:"; //this very simple delim should be safe for any kind of path.
    private static final Logger LOGGER = Logging.getLogger(DaemonStartupCommunication.class);

    public void printDaemonStarted(PrintStream target, Long pid, File daemonLog) {
        target.println(daemonStartedMessage(pid, daemonLog));
        //ibm vm 1.6 + windows XP gotchas:
        //we need to print something else to the stream after we print the daemon greeting.
        //without it, the parent hangs without receiving the message above (flushing does not help).
        LOGGER.debug("Completed writing the daemon greeting. Closing streams...");
        //btw. the ibm vm+winXP also has some issues detecting closed streams by the child but we handle this problem differently.
    }

    String daemonStartedMessage(Long pid, File daemonLog) {
        return daemonGreeting() + DELIM + pid + DELIM + daemonLog;
    }

    public DaemonDiagnostics readDiagnostics(String message) {
        //TODO SF dont assume the message has correct format
        String[] split = message.split(DELIM);
        String pidString = split[1];
        Long pid = pidString.equals("null")? null : Long.valueOf(pidString);
        File daemonLog = new File(split[2]);
        return new DaemonDiagnostics(daemonLog, pid);
    }

    public boolean containsGreeting(String message) {
        if (message == null) {
            throw new IllegalArgumentException("Unable to detect the daemon greeting because the input message is null!");
        }
        return message.contains(daemonGreeting());
    }

    private static String daemonGreeting() {
        return DaemonMessages.ABOUT_TO_CLOSE_STREAMS;
    }
}
