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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.platform.base.internal.toolchain.ResolvedTool;
import org.gradle.play.internal.run.DefaultPlayRunSpec;
import org.gradle.play.internal.run.PlayApplicationRunner;
import org.gradle.play.internal.run.PlayApplicationRunnerToken;
import org.gradle.play.internal.run.PlayRunSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * A Task to run a play application.
 */
@Incubating
public class PlayRun extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(PlayRun.class);

    private int httpPort;

    @InputFile
    private File applicationJar;

    @InputFile
    private File assetsJar;

    @InputFiles
    private FileCollection runtimeClasspath;

    private BaseForkOptions forkOptions;

    private PlayApplicationRunnerToken runnerToken;
    private ResolvedTool<PlayApplicationRunner> playApplicationRunnerTool;

    /**
     * fork options for the running a play application.
     */
    public BaseForkOptions getForkOptions() {
        if (forkOptions == null) {
            forkOptions = new BaseForkOptions();
        }
        return forkOptions;
    }

    @TaskAction
    public void run() {
        ProgressLoggerFactory progressLoggerFactory = getServices().get(ProgressLoggerFactory.class);
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(PlayRun.class)
                .start("Start Play server", "Starting Play");

        int httpPort = getHttpPort();
        FileCollection applicationJars = new SimpleFileCollection(applicationJar, assetsJar);
        applicationJars = applicationJars.plus(runtimeClasspath);
        PlayRunSpec spec = new DefaultPlayRunSpec(applicationJars, getProject().getProjectDir(), getForkOptions(), httpPort);

        try {
            runnerToken = playApplicationRunnerTool.get().start(spec);
            progressLogger.completed();
            progressLogger = progressLoggerFactory.newOperation(PlayRun.class)
                    .start(String.format("Run Play App at http://localhost:%d/", httpPort),
                            String.format("Running at http://localhost:%d/ (stop with ctrl+d)", httpPort));
            waitForCtrlD();
            runnerToken.stop();
        } finally {
            progressLogger.completed();
        }
    }

    private void waitForCtrlD() {
        while (true) {
            try {
                int c = System.in.read();
                if (c == -1 || c == 4) {
                    // STOP on Ctrl-D or EOF.
                    logger.info("received end of stream (ctrl+d)");
                    return;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public void setApplicationJar(File applicationJar) {
        this.applicationJar = applicationJar;
    }

    public void setAssetsJar(File assetsJar) {
        this.assetsJar = assetsJar;
    }

    public void setRuntimeClasspath(FileCollection runtimeClasspath) {
        this.runtimeClasspath = runtimeClasspath;
    }

    public void setPlayApplicationRunnerTool(ResolvedTool<PlayApplicationRunner> playApplicationRunnerTool) {
        this.playApplicationRunnerTool = playApplicationRunnerTool;
    }
}
