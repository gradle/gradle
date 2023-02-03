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

import java.io.File;
import java.util.List;

public class DefaultDaemonServerConfiguration implements DaemonServerConfiguration {

    private final String daemonUid;
    private final File daemonBaseDir;
    private final int idleTimeoutMs;
    private final int periodicCheckIntervalMs;
    private final boolean singleUse;
    private final DaemonParameters.Priority priority;
    private final List<String> jvmOptions;

    public DefaultDaemonServerConfiguration(String daemonUid, File daemonBaseDir, int idleTimeoutMs, int periodicCheckIntervalMs, boolean singleUse, DaemonParameters.Priority priority, List<String> jvmOptions) {
        this.daemonUid = daemonUid;
        this.daemonBaseDir = daemonBaseDir;
        this.idleTimeoutMs = idleTimeoutMs;
        this.periodicCheckIntervalMs = periodicCheckIntervalMs;
        this.singleUse = singleUse;
        this.priority = priority;
        this.jvmOptions = jvmOptions;
    }

    @Override
    public File getBaseDir() {
        return daemonBaseDir;
    }

    @Override
    public int getIdleTimeout() {
        return idleTimeoutMs;
    }

    @Override
    public int getPeriodicCheckIntervalMs() {
        return periodicCheckIntervalMs;
    }

    @Override
    public String getUid() {
        return daemonUid;
    }

    @Override
    public DaemonParameters.Priority getPriority() {
        return priority;
    }

    @Override
    public List<String> getJvmOptions() {
        return jvmOptions;
    }

    @Override
    public boolean isSingleUse() {
        return singleUse;
    }
}
