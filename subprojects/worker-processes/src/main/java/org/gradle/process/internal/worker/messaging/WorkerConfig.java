/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.process.internal.worker.messaging;

import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress;
import org.gradle.process.internal.worker.WorkerProcessContext;

import java.io.Serializable;

/**
 * All configuration options to be transferred to a worker process during worker startup.
 */
public class WorkerConfig {
    private final LogLevel logLevel;
    private final boolean publishJvmMemoryInfo;
    private final String gradleUserHomeDirPath;
    private final MultiChoiceAddress serverAddress;
    private final long workerId;
    private final String displayName;
    private final Action<? super WorkerProcessContext> workerAction;

    public WorkerConfig(LogLevel logLevel, boolean publishJvmMemoryInfo, String gradleUserHomeDirPath, MultiChoiceAddress serverAddress, long workerId, String displayName, Action<? super WorkerProcessContext> workerAction) {
        this.logLevel = logLevel;
        this.publishJvmMemoryInfo = publishJvmMemoryInfo;
        this.gradleUserHomeDirPath = gradleUserHomeDirPath;
        this.serverAddress = serverAddress;
        this.workerId = workerId;
        this.displayName = displayName;
        this.workerAction = workerAction;

        assert workerAction instanceof Serializable;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    /**
     * @return True if process info should be published. False otherwise.
     */
    public boolean shouldPublishJvmMemoryInfo() {
        return publishJvmMemoryInfo;
    }

    /**
     * @return The absolute path to the Gradle user home directory.
     */
    public String getGradleUserHomeDirPath() {
        return gradleUserHomeDirPath;
    }

    public MultiChoiceAddress getServerAddress() {
        return serverAddress;
    }

    public long getWorkerId() {
        return workerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Action<? super WorkerProcessContext> getWorkerAction() {
        return workerAction;
    }
}
