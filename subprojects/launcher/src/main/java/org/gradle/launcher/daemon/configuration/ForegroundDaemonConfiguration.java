/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.launcher.daemon.configuration;

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.process.internal.CurrentProcess;

import java.io.File;

public class ForegroundDaemonConfiguration extends DefaultDaemonServerConfiguration {
    public ForegroundDaemonConfiguration(String daemonUid, File daemonBaseDir, int idleTimeoutMs, int periodicCheckIntervalMs, FileCollectionFactory fileCollectionFactory) {
        // Foreground daemon cannot be 'told' what's his startup options as the client sits in the same process so we will infer the jvm opts from the inputArguments()
        super(daemonUid, daemonBaseDir, idleTimeoutMs, periodicCheckIntervalMs, false, DaemonParameters.Priority.NORMAL, new CurrentProcess(fileCollectionFactory).getJvmOptions().getAllImmutableJvmArgs());
    }
}
