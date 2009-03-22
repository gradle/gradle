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

import org.gradle.api.GradleException;
import org.gradle.util.GUtil;
import org.hamcrest.Matcher;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.File;
import java.util.*;

public class ForkingGradleExecuter implements GradleExecuter {
    private final GradleDistribution distribution;
    private File workingDir;
    private int logLevel = Executer.LIFECYCLE;
    private List<String> tasks = new ArrayList<String>();
    private List<String> args = new ArrayList<String>();

    public ForkingGradleExecuter(GradleDistribution distribution) {
        this.distribution = distribution;
        workingDir = distribution.getGradleHomeDir();
    }

    public GradleExecuter inDirectory(File directory) {
        workingDir = directory;
        return this;
    }

    public GradleExecuter withSearchUpwards() {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withTasks(String... names) {
        tasks = Arrays.asList(names);
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

    public GradleExecuter withArguments(String... args) {
        this.args.addAll(Arrays.asList(args));
        return this;
    }

    public GradleExecuter withQuietLogging() {
        logLevel = Executer.QUIET;
        return this;
    }

    public ExecutionResult run() {
        Executer.execute(distribution.getGradleHomeDir().getAbsolutePath(), workingDir.getAbsolutePath(),
                GUtil.addLists(args, tasks), new HashMap(), "", logLevel, false);
        return new ForkedExecutionResult();
    }

    public ExecutionFailure runWithFailure() {
        Map result = Executer.execute(distribution.getGradleHomeDir().getAbsolutePath(), workingDir.getAbsolutePath(),
                GUtil.addLists(args, tasks), new HashMap(), "", logLevel, true);
        return new ForkedExecutionFailure(result);
    }

    private static class ForkedExecutionResult implements ExecutionResult {
        public void assertTasksExecuted(String... taskPaths) {
            throw new UnsupportedOperationException();
        }
    }

    private static class ForkedExecutionFailure implements ExecutionFailure {
        private final Map result;

        public ForkedExecutionFailure(Map result) {
            this.result = result;
        }

        public GradleException getFailure() {
            throw new UnsupportedOperationException();
        }

        public void assertHasLineNumber(int lineNumber) {
            throw new UnsupportedOperationException();
        }

        public void assertHasFileName(String filename) {
            throw new UnsupportedOperationException();
        }

        public void assertHasDescription(String description) {
            assertThat(result.get("error").toString(), containsString(description));
        }

        public void assertDescription(Matcher<String> matcher) {
            throw new UnsupportedOperationException();
        }

        public void assertHasContext(String context) {
            assertThat(result.get("error").toString(), containsString(context));
        }

        public void assertContext(Matcher<String> matcher) {
            throw new UnsupportedOperationException();
        }
    }
}
