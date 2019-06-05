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
import org.gradle.process.internal.StreamsHandler;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Scanner;
import java.util.concurrent.Executor;

public class DaemonOutputConsumer implements StreamsHandler {

    private final static Logger LOGGER = Logging.getLogger(DaemonOutputConsumer.class);
    DaemonStartupCommunication startupCommunication = new DaemonStartupCommunication();

    private String processOutput;
    private InputStream processStdOutput;

    @Override
    public void connectStreams(Process process, String processName, Executor executor) {
        processStdOutput = process.getInputStream();
    }

    @Override
    public void start() {
        if (processStdOutput == null) {
            throw new IllegalStateException("Cannot start consuming daemon output because streams have not been connected first.");
        }
        LOGGER.debug("Starting consuming the daemon process output.");

        // Wait for the process' stdout to indicate that the process has been started successfully
        StringWriter output = new StringWriter();
        Scanner scanner = new Scanner(processStdOutput);
        PrintWriter printer = new PrintWriter(output);
        try {
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                LOGGER.debug("daemon out: {}", line);
                printer.println(line);
                if (startupCommunication.containsGreeting(line)) {
                    break;
                }
            }
        } finally {
            scanner.close();
        }
        processOutput = output.toString();
    }

    public String getProcessOutput() {
        if (processOutput == null) {
            throw new IllegalStateException("Unable to get process output as consuming has not finished yet.");
        }
        return processOutput;
    }

    @Override
    public void stop() {
    }

    @Override
    public void disconnect() {
    }
}
