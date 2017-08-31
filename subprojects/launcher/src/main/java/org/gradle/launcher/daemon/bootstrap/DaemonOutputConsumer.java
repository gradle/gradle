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
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.process.internal.streams.ExecOutputHandleRunner;
import org.gradle.process.internal.streams.StreamsHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Scanner;

public class DaemonOutputConsumer implements StreamsHandler {

    private final static Logger LOGGER = Logging.getLogger(DaemonOutputConsumer.class);
    private final InputStream stdInput;

    private StringWriter output;
    private ManagedExecutor executor;
    private Runnable streamConsumer;
    DaemonStartupCommunication startupCommunication = new DaemonStartupCommunication();
    private String processOutput;
    private ExecOutputHandleRunner standardInputRunner;

    public DaemonOutputConsumer(InputStream stdInput) {
        this.stdInput = stdInput;
    }

    public void connectStreams(final Process process, String processName, ExecutorFactory executorFactory) {
        if (process == null || processName == null) {
            throw new IllegalArgumentException("Cannot connect streams because provided process or its name is null");
        }
        standardInputRunner = new ExecOutputHandleRunner("write standard input into: " + processName, stdInput, process.getOutputStream());
        executor = executorFactory.create("Read output from: " + processName);
        final InputStream inputStream = process.getInputStream();
        streamConsumer = new Runnable() {
            public void run() {
                Scanner scanner = new Scanner(inputStream);
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
            }
        };
    }

    public void start() {
        if (executor == null || streamConsumer == null) {
            throw new IllegalStateException("Cannot start consuming daemon output because streams have not been connected first.");
        }
        LOGGER.debug("Starting consuming the daemon process output.");
        output = new StringWriter();
        executor.execute(standardInputRunner);
        executor.execute(streamConsumer);
    }

    public String getProcessOutput() {
        if (processOutput == null) {
            throw new IllegalStateException("Unable to get process output as consuming has not finished yet.");
        }
        return processOutput;
    }

    public void stop() {
        if (executor == null || output == null) {
            throw new IllegalStateException("Unable to stop output consumer. Was it started?.");
        }
        try {
            standardInputRunner.closeInput();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        executor.stop();
        processOutput = output.toString();
    }
}
