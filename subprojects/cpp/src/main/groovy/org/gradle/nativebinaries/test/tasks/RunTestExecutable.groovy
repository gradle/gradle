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

package org.gradle.nativebinaries.test.tasks

import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.ConsoleRenderer
import org.gradle.process.ProcessForkOptions

/**
 * Runs a compiled and installed test executable.
 */
@Incubating
public class RunTestExecutable extends AbstractExecTask {
    /**
     * {@inheritDoc}
     */
    @Input
    public String getExecutable() {
        return super.getExecutable();
    }

    /**
     * The directory where the results should be generated.
     */
    @OutputDirectory File outputDir

    /**
     * Should the build continue if a test fails, or should the build break?
     */
    @Input boolean ignoreFailures

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable commandLine(Object... arguments) {
        super.commandLine(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable args(Object... args) {
        super.args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable setArgs(Iterable<?> arguments) {
        super.setArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable executable(Object executable) {
        super.executable(executable);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable workingDir(Object dir) {
        super.workingDir(dir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable environment(String name, Object value) {
        super.environment(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable environment(Map<String, ?> environmentVariables) {
        super.environment(environmentVariables);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable copyTo(ProcessForkOptions target) {
        super.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable setStandardInput(InputStream inputStream) {
        super.setStandardInput(inputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable setStandardOutput(OutputStream outputStream) {
        super.setStandardOutput(outputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public RunTestExecutable setErrorOutput(OutputStream outputStream) {
        super.setErrorOutput(outputStream);
        return this;
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
}
