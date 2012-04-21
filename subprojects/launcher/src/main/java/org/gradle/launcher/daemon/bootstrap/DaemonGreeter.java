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

import org.gradle.api.GradleException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.logging.DaemonMessages;

/**
 * by Szczepan Faber, created at: 1/19/12
 */
public class DaemonGreeter {
    private final DocumentationRegistry documentationRegistry;

    public DaemonGreeter(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
    }

    public DaemonDiagnostics parseDaemonOutput(String output) {
        if (!output.contains(DaemonMessages.ABOUT_TO_CLOSE_STREAMS)) {
            throw new GradleException(DaemonMessages.UNABLE_TO_START_DAEMON
                    + "\n" + processOutput(output));
        }
        String[] lines = output.split("\n");
        String lastLine =  lines[lines.length-1];
        return new DaemonStartupCommunication().readDiagnostics(lastLine);
    }

    private String processOutput(String output) {
        StringBuilder sb = new StringBuilder();
        sb.append("This problem might be caused by incorrect configuration of the daemon.\n");
        sb.append("For example, an unrecognized jvm option is used.\n");
        sb.append("Please refer to the user guide chapter on the daemon at ");
        sb.append(documentationRegistry.getDocumentationFor("gradle_daemon"));
        sb.append("\n");
        sb.append("Please read below process output to find out more:\n");
        sb.append("-----------------------\n");
        sb.append(output);
        return sb.toString();
    }
}