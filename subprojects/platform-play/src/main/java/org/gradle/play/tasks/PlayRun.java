/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.play.internal.run.PlayApplicationRunnerToken;
import org.gradle.play.internal.run.PlayRunSpec;
import org.gradle.play.internal.run.PlayRunWorkerManager;
import org.gradle.process.internal.WorkerProcessBuilder;

import javax.inject.Inject;

/**
 * A Task to run a play application.
 */
public class PlayRun extends ConventionTask {


    private FileCollection classpath;
    private FileCollection playAppClasspath;

    private int httpPort;

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    private PlayApplicationRunnerToken runnerToken;

    @Inject
    public LoggingManagerInternal getLogging() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void run() {
        ProgressLoggerFactory progressLoggerFactory = getServices().get(ProgressLoggerFactory.class);
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(PlayRun.class)
                .start("Start Play server", "Starting Play");

        PlayRunSpec spec = generateSpec();
        PlayRunWorkerManager manager = new PlayRunWorkerManager();

        try {
            runnerToken = manager.start(getProject().getProjectDir(), getWorkerProcessBuilderFactory(), getClasspath(), spec);
            progressLogger = progressLoggerFactory.newOperation(PlayRun.class)
                    .start(String.format("Run Play App at http://localhost:%d/", getHttpPort()),
                            String.format("Running at http://localhost:%d/", getHttpPort()));
            runnerToken.waitForStop();
        } finally {
            progressLogger.completed();
        }
    }

    @Inject
    public Factory<WorkerProcessBuilder> getWorkerProcessBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    private PlayRunSpec generateSpec() {
        return new PlayRunSpec(getClasspath().getFiles(), getProject().getProjectDir(), getPlayAppClasspath().getFiles());
    }

    public FileCollection getPlayAppClasspath() {
        return playAppClasspath;
    }

    public void setPlayAppClasspath(FileCollection playAppClasspath) {
        this.playAppClasspath = playAppClasspath;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }
}
