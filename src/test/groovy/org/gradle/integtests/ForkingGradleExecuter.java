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
package org.gradle.integtests;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class ForkingGradleExecuter implements GradleExecuter {
    private final GradleDistribution distribution;
    private File workingDir;
    private int logLevel = Executer.LIFECYCLE;
    private final List<String> tasks = new ArrayList<String>();

    public ForkingGradleExecuter(GradleDistribution distribution) {
        this.distribution = distribution;
    }

    public GradleExecuter inDirectory(File directory) {
        workingDir = directory;
        return this;
    }

    public GradleExecuter withSearchUpwards() {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withTasks(String... names) {
        tasks.addAll(Arrays.asList(names));
        return this;
    }

    public GradleExecuter withTaskList() {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withDependencyList() {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingSettingsFile(TestFile settingsFile) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingSettingsFile(File settingsFile) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter usingBuildScript(String script) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withQuietLogging() {
        logLevel = Executer.QUIET;
        return this;
    }

    public ExecutionResult run() {
        Executer.execute(distribution.getGradleHomeDir().getAbsolutePath(), workingDir.getAbsolutePath(), tasks,
                new HashMap(), "", logLevel, false);
        return new ForkedExecutionResult();
    }

    public ExecutionFailure runWithFailure() {
        throw new UnsupportedOperationException();
    }

    private static class ForkedExecutionResult implements ExecutionResult {
        public void assertTasksExecuted(String... taskPaths) {
            throw new UnsupportedOperationException();
        }
    }
}
