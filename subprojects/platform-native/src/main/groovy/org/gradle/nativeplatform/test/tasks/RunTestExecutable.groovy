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

package org.gradle.nativeplatform.test.tasks
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.ConsoleRenderer
/**
 * Runs a compiled and installed test executable.
 */
@Incubating
@SuppressWarnings("unchecked")
public class RunTestExecutable extends AbstractExecTask<RunTestExecutable> {
    public RunTestExecutable() {
        super(RunTestExecutable.class);
    }

    /**
     * The directory where the results should be generated.
     */
    @OutputDirectory File outputDir

    /**
     * Should the build continue if a test fails, or should the build break?
     */
    @Input boolean ignoreFailures

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
