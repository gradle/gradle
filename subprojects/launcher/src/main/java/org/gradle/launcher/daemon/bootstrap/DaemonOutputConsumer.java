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
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.process.internal.streams.StreamsHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Scanner;

/**
* by Szczepan Faber, created at: 4/28/12
*/
public class DaemonOutputConsumer implements StreamsHandler {

    private final static Logger LOGGER = Logging.getLogger(DaemonOutputConsumer.class);
    //TODO SF add some lifecycle validation, coverage, exception handling

    private StoppableExecutor executor;
    private final StringWriter output = new StringWriter();
    private Runnable streamConsumer;

    public void start() {
        if (executor == null || streamConsumer == null) {
            throw new IllegalStateException("Cannot start handling streams. #connectStreams not called first.");
        }
        LOGGER.debug("Starting consuming the daemon process output.");
        executor.execute(streamConsumer);
    }

    public void connectStreams(final Process process, String processName) {
        executor = new DefaultExecutorFactory().create("Read output from: " + processName);
        streamConsumer = new Runnable() {
            public void run() {
                Scanner scanner = new Scanner(process.getInputStream());
                PrintWriter printer = new PrintWriter(output);
                try {
                    while (scanner.hasNext()) {
                        String line = scanner.nextLine();
                        LOGGER.debug("daemon out: {}", line);
                        printer.println(line);
                        if (new DaemonStartupCommunication().containsGreeting(line)) {
                            break;
                        }
                    }
                } finally {
                    scanner.close();
                }
            }
        };
    }

    public String getProcessOutput() {
        return output.toString();
    }

    public void stop() {
        executor.stop();
    }
}
