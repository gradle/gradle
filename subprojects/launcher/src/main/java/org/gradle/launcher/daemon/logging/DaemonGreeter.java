/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.launcher.daemon.logging;

import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;

import java.io.PrintStream;
import java.util.List;

/**
 * by Szczepan Faber, created at: 1/19/12
 */
public class DaemonGreeter {
    
    public void sendGreetingAndClose(PrintStream output) {
        synchronized (output) {
            output.println(DaemonMessages.PROCESS_STARTED);
            output.close();
        }
    }

    public void verifyGreetingReceived(Process process) {
        List<String> lines;
        try {
            lines = IOUtils.readLines(process.getInputStream());
        } catch (Exception e) {
            throw new GradleException("Unable to get a greeting message from the daemon process."
                    + " Most likely the daemon process cannot be started.", e);
        }

        String lastMessage = lines.get(lines.size() - 1);
        if (!lastMessage.equals(DaemonMessages.PROCESS_STARTED)) {
            // consider waiting a bit for the exit value
            // if exit value not provided warn that the daemon didn't exit
            int exitValue;
            try {
                exitValue = process.exitValue();
            } catch (IllegalThreadStateException e) {
                throw new GradleException(
                    DaemonMessages.UNABLE_TO_START_DAEMON + " However, it appears the process hasn't exited yet."
                    + "\n" + processOutput(lines));
            }
            throw new GradleException(DaemonMessages.UNABLE_TO_START_DAEMON + " The exit value was: " + exitValue + "."
                    + "\n" + processOutput(lines));
        }
    }

    private String processOutput(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("This problem might be caused by incorrect configuration of the daemon.\n");
        sb.append("For example, an unrecognized jvm option is used.\n");
        sb.append("Please refer to the user guide chapter on the daemon.\n");
        sb.append("Please read below process output to find out more:\n");
        sb.append("-----------------------\n");

        for (String line : lines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
