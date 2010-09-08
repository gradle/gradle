/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.invocation.Gradle;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

class BuildProgressLogger extends BuildAdapter implements TaskExecutionGraphListener {
    private ProgressLogger progressLogger;
    private final ProgressLoggerFactory progressLoggerFactory;
    private Gradle gradle;

    public BuildProgressLogger(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    @Override
    public void buildStarted(Gradle gradle) {
        if (gradle.getParent() == null) {
            progressLogger = progressLoggerFactory.start(BuildProgressLogger.class.getName());
            progressLogger.progress("Loading");
            this.gradle = gradle;
        }
    }

    public void graphPopulated(TaskExecutionGraph graph) {
        if (graph == gradle.getTaskGraph()) {
            progressLogger.progress("Building");
        }
    }

    @Override
    public void buildFinished(BuildResult result) {
        if (result.getGradle() == gradle) {
            progressLogger.completed();
            progressLogger = null;
            gradle = null;
        }
    }
}
