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

import org.gradle.internal.Try;
import org.gradle.launcher.daemon.startup.DaemonStartupCommunication;
import org.gradle.launcher.daemon.startup.DaemonStartupInfo;
import org.gradle.process.internal.streams.StreamsHandler;

import java.io.InputStream;
import java.util.concurrent.Executor;

public class DaemonOutputConsumer implements StreamsHandler {

    private InputStream processStdOutput;
    private Try<DaemonStartupInfo> response;

    @Override
    public void connectStreams(Process process, String processName, Executor executor) {
        processStdOutput = process.getInputStream();
    }

    @Override
    @SuppressWarnings("DefaultCharset")
    public void start() {
        if (processStdOutput == null) {
            throw new IllegalStateException("Cannot start consuming daemon output because streams have not been connected first.");
        }
        this.response = Try.ofFailable(() -> DaemonStartupCommunication.readStartupInfoFromDaemonOutput(processStdOutput));
    }

    public Try<DaemonStartupInfo> getResponse() {
        if (response == null) {
            throw new IllegalStateException("Unable to get response as consuming has not finished yet.");
        }
        return response;
    }

    @Override
    public void removeStartupContext() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void disconnect() {
    }

}
