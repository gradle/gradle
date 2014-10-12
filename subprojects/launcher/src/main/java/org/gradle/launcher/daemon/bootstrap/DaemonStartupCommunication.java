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
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.inet.MultiChoiceAddress;

import java.io.File;
import java.io.PrintStream;

public class DaemonStartupCommunication {

    private static final String DELIM = ";:"; //this very simple delim should be safe for any kind of path.
    private static final Logger LOGGER = Logging.getLogger(DaemonStartupCommunication.class);

    public void printDaemonStarted(PrintStream target, Long pid, String uid, Address address, File daemonLog) {
        target.print(daemonGreeting());
        target.print(DELIM);
        target.print(pid);
        target.print(DELIM);
        target.print(uid);
        target.print(DELIM);
        MultiChoiceAddress multiChoiceAddress = (MultiChoiceAddress) address;
        target.print(multiChoiceAddress.getPort());
        target.print(DELIM);
        target.print(daemonLog.getPath());
        target.println();

        //ibm vm 1.6 + windows XP gotchas:
        //we need to print something else to the stream after we print the daemon greeting.
        //without it, the parent hangs without receiving the message above (flushing does not help).
        LOGGER.debug("Completed writing the daemon greeting. Closing streams...");
        //btw. the ibm vm+winXP also has some issues detecting closed streams by the child but we handle this problem differently.
    }

    public DaemonStartupInfo readDiagnostics(String message) {
        //Assuming the message has correct format. Not bullet proof, but seems to work ok for now.
        String[] split = message.trim().split(DELIM);
        String pidString = split[1];
        Long pid = pidString.equals("null")? null : Long.valueOf(pidString);
        String uid = split[2];
        int port = Integer.parseInt(split[3]);
        File daemonLog = new File(split[4]);
        return new DaemonStartupInfo(uid, port, new DaemonDiagnostics(daemonLog, pid));
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
