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
import org.gradle.internal.CompositeStoppable;
import org.gradle.process.internal.streams.ProcessStreamHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Scanner;

/**
* by Szczepan Faber, created at: 4/28/12
*/
public class DaemonOutputConsumer implements ProcessStreamHandler {

    private final static Logger LOGGER = Logging.getLogger(DaemonOutputConsumer.class);

    private final StringWriter output = new StringWriter();
    DaemonStartupCommunication startupCommunication = new DaemonStartupCommunication();
    private String processOutput;

    public String getProcessOutput() {
        if (processOutput == null) {
            throw new IllegalStateException("Unable to get process output as consuming has not finished yet.");
        }
        return processOutput;
    }

    public void handleStream(InputStream source, OutputStream destination) {
        Scanner scanner = new Scanner(source);
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
            new CompositeStoppable(source, destination).stop(); //TODO SF duplicated
            processOutput = output.toString();
        }
    }
}
