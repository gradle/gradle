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
package org.gradle.nativeplatform.test.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.*;
import org.gradle.internal.logging.ConsoleRenderer;

import java.io.File;

/**
 * Runs a compiled and installed test executable.
 */
@Incubating
@SuppressWarnings("unchecked")
@ParallelizableTask
public class RunTestExecutable extends AbstractExecTask<RunTestExecutable> {
    /**
     * The directory where the results should be generated.
     */
    private File outputDir;
    /**
     * Should the build continue if a test fails, or should the build break?
     */
    private boolean ignoreFailures;

    public RunTestExecutable() {
        super(RunTestExecutable.class);
    }

    @TaskAction
    @Override
    protected void exec() {
        // Make convention mapping work
        setExecutable(getExecutable());
        setWorkingDir(getOutputDir());

        try {
            super.exec();
        } catch (Exception e) {
            handleTestFailures(e);
        }

    }

    private void handleTestFailures(Exception e) {
        String message = "There were failing tests";
        String resultsUrl = new ConsoleRenderer().asClickableFileUrl(getOutputDir());
        message = message.concat(". See the results at: " + resultsUrl);

        if (isIgnoreFailures()) {
            getLogger().warn(message);
        } else {
            throw new GradleException(message, e);
        }

    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    @Input
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

}
