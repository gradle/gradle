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

import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.play.internal.run.*;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.toolchain.PlayToolChain;
import org.gradle.process.internal.WorkerProcessBuilder;

import javax.inject.Inject;

/**
 * A Task to run a play application.
 */
public class PlayRun extends ConventionTask {


    private FileCollection classpath;

    private int httpPort;

    private BaseForkOptions forkOptions;

    private PlayApplicationRunnerToken runnerToken;
    private PlayPlatform targetPlatform;


    /**
     * fork options for the running a play application.
     */
    public BaseForkOptions getForkOptions() {
        if (forkOptions == null) {
            forkOptions = new BaseForkOptions();
        }
        return forkOptions;
    }

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

        int httpPort = getHttpPort();

        PlayRunSpec spec = new DefaultPlayRunSpec(getClasspath().getFiles(), getProject().getProjectDir(), forkOptions, httpPort);
        PlayApplicationRunner manager = ((PlayToolChainInternal) getToolChain()).createPlayApplicationRunner(getWorkerProcessBuilderFactory(), getTargetPlatform(), spec);

        try {
            runnerToken = manager.start();

            progressLogger = progressLoggerFactory.newOperation(PlayRun.class)
                    .start(String.format("Run Play App at http://localhost:%d/", httpPort),
                            String.format("Running at http://localhost:%d/", httpPort));
            runnerToken.waitForStop();
        } finally {
            progressLogger.completed();
        }
    }

    @Inject
    public Factory<WorkerProcessBuilder> getWorkerProcessBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    @Incubating
    @Inject
    public PlayToolChain getToolChain() {
        // Implementation is generated
        throw new UnsupportedOperationException();
    }

    public void setTargetPlatform(PlayPlatform targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public PlayPlatform getTargetPlatform() {
        return targetPlatform;
    }
}
