/*
 * Copyright 2008 the original author or authors.
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

import junit.framework.AssertionFailedError;
import org.apache.commons.io.FileUtils;
import org.gradle.Gradle;
import org.gradle.CacheUsage;
import org.gradle.StartParameter;
import org.gradle.BuildResult;
import org.gradle.BuildListener;
import org.gradle.execution.BuiltInTasksBuildExecuter;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.Task;
import org.gradle.api.GradleScriptException;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.invocation.Build;
import org.gradle.api.initialization.Settings;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import org.hamcrest.Matcher;
import static org.junit.Assert.*;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.ArrayList;

public class AbstractIntegrationTest {
    private File testDir;

    @Before
    public void setupTestDir() throws IOException {
        testDir = HelperUtil.makeNewTestDir().getCanonicalFile();
    }

    public File getTestDir() {
        return testDir;
    }

    public TestFile testFile(String name) {
        return new TestFile(new File(getTestDir(), name));
    }

    public TestFile testFile(File dir, String name) {
        return new TestFile(new File(dir, name));
    }

    protected File getTestBuildFile(String name) {
        URL resource = getClass().getResource("testProjects/" + name);
        assertThat(resource, notNullValue());
        assertThat(resource.getProtocol(), equalTo("file"));
        File sourceFile;
        try {
            sourceFile = new File(resource.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("Could not locate test build file '%s'.", name));
        }

        File destFile = testFile(sourceFile.getName()).asFile();
        try {
            FileUtils.copyFile(sourceFile, destFile);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not copy test build file '%s' to '%s'", sourceFile, destFile), e);
        }
        return destFile;
    }

    protected StartParameter startParameter() {
        StartParameter parameter = new StartParameter();
        parameter.setGradleHomeDir(testFile("gradle-home").asFile());

        testFile("gradle-home/gradle-imports").writelns(
                "import org.gradle.api.*",
                "import static org.junit.Assert.*",
                "import static org.hamcrest.Matchers.*");

        testFile("gradle-home/plugin.properties").writelns(
                "groovy=org.gradle.api.plugins.GroovyPlugin"
        );

        parameter.setGradleUserHomeDir(testFile("user-home").asFile());

        parameter.setSearchUpwards(false);
        parameter.setCacheUsage(CacheUsage.ON);
        parameter.setCurrentDir(getTestDir());

        return parameter;
    }

    protected GradleExecution inTestDirectory() {
        return inDirectory(testDir);
    }
    
    protected GradleExecution inDirectory(File file) {
        StartParameter parameter = startParameter();
        parameter.setCurrentDir(file);
        return new GradleExecution(parameter);
    }

    protected GradleExecution usingBuildFile(TestFile file) {
        return usingBuildFile(file.asFile());
    }
    
    protected GradleExecution usingBuildFile(File file) {
        StartParameter parameter = startParameter();
        parameter.setBuildFile(file);
        return new GradleExecution(parameter);
    }

    protected GradleExecution usingBuildScript(String script) {
        StartParameter parameter = startParameter();
        parameter.useEmbeddedBuildFile(script);
        return new GradleExecution(parameter);
    }

    public static class TestFile {
        private final File file;

        public TestFile(File file) {
            this.file = file.getAbsoluteFile();
        }

        public TestFile writelns(String... lines) {
            Formatter formatter = new Formatter();
            for (String line : lines) {
                formatter.format("%s%n", line);
            }
            return write(formatter);
        }
        
        public TestFile write(Object content) {
            try {
                FileUtils.writeStringToFile(file, content.toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        public void touch() {
            try {
                FileUtils.touch(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public File asFile() {
            return file;
        }

        @Override
        public String toString() {
            return file.getPath();
        }

        public TestFile writelns(List<String> lines) {
            Formatter formatter = new Formatter();
            for (String line : lines) {
                formatter.format("%s%n", line);
            }
            return write(formatter);
        }
    }
    
    public static class GradleExecution {
        private final StartParameter parameter;
        private final List<String> tasks = new ArrayList<String>();
        private final List<Task> planned = new ArrayList<Task>();

        public GradleExecution(StartParameter parameter) {
            this.parameter = parameter;
        }

        public GradleExecution withSearchUpwards() {
            parameter.setSearchUpwards(true);
            return this;
        }

        public GradleExecutionResult runTasks(String... names) {
            parameter.setTaskNames(Arrays.asList(names));
            return execute();
        }

        public GradleExecutionResult showTaskList() {
            parameter.setBuildExecuter(new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.TASKS));
            return execute();
        }

        public GradleExecutionResult showDependencyList() {
            parameter.setBuildExecuter(new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.DEPENDENCIES));
            return execute();
        }
        
        private GradleExecutionResult execute() {
            
            Gradle gradle = Gradle.newInstance(parameter);
            gradle.addBuildListener(new ListenerImpl());
            BuildResult result = gradle.run();
            result.rethrowFailure();
            return new GradleExecutionResult(tasks);
        }


        public GradleExecutionFailure runTasksAndExpectFailure(String... names) {
            try {
                runTasks(names);
                throw new AssertionFailedError("expected build to fail.");
            } catch (GradleException e) {
                return new GradleExecutionFailure(e);
            }
        }

        public GradleExecution usingSettingsFile(TestFile settingsFile) {
            return usingSettingsFile(settingsFile.asFile());
        }

        public GradleExecution usingSettingsFile(File settingsFile) {
            parameter.setSettingsFile(settingsFile);
            return this;
        }

        public GradleExecution usingBuildScript(String script) {
            parameter.useEmbeddedBuildFile(script);
            return this;
        }

        private class ListenerImpl implements BuildListener {
            private TaskListenerImpl listener = new TaskListenerImpl();

            public void buildStarted(StartParameter startParameter) {
            }

            public void settingsEvaluated(Settings settings) {
            }

            public void projectsLoaded(Build build) {
            }

            public void projectsEvaluated(Build build) {
            }

            public void taskGraphPopulated(TaskExecutionGraph graph) {
                planned.clear();
                planned.addAll(graph.getAllTasks());
                graph.addTaskExecutionListener(listener);
            }

            public void buildFinished(BuildResult result) {
            }
        }

        private class TaskListenerImpl implements TaskExecutionListener {
            private Task current;

            public void beforeExecute(Task task) {
                assertThat(current, nullValue());
                assertTrue(planned.contains(task));
                current = task;
            }

            public void afterExecute(Task task, Throwable failure) {
                assertThat(task, sameInstance(current));
                current = null;
                tasks.add(task.getPath());
            }
        }
    }

    public static class GradleExecutionResult {
        private final List<String> plannedTasks;

        public GradleExecutionResult(List<String> plannedTasks) {
            this.plannedTasks = plannedTasks;
        }

        public void assertTasksExecuted(String... taskPaths) {
            List<String> expected = Arrays.asList(taskPaths);
            assertThat(plannedTasks, equalTo(expected));
        }
    }

    public static class GradleExecutionFailure {
        private final GradleException failure;

        public GradleExecutionFailure(GradleException failure) {
            if (failure instanceof GradleScriptException) {
                this.failure = ((GradleScriptException) failure).getReportableException();
            } else {
                this.failure = failure;
            }
        }

        public GradleException getFailure() {
            return failure;
        }

        public void assertHasLineNumber(int lineNumber) {
            assertThat(failure.getMessage(), containsString(String.format(" line: %d", lineNumber)));
        }

        public void assertHasFileName(String filename) {
            assertThat(failure.getMessage(), startsWith(String.format("%s", filename)));
        }

        public void assertHasDescription(String description) {
            assertThat(failure.getCause().getMessage(), endsWith(description));
        }

        public void assertDescription(Matcher<String> matcher)
        {
            assertThat(failure.getCause().getMessage(), matcher);
        }

        public void assertHasContext(String context) {
            assertThat(failure.getMessage(), containsString(context));
        }

        public void assertContext(Matcher<String> matcher)
        {
            assertThat(failure.getMessage(), matcher);
        }
    }

}
