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

package org.gradle.integtests.fixtures;

import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.util.GUtil;
import org.gradle.util.OperatingSystem;
import org.gradle.util.TestFile;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class ForkingGradleExecuter extends AbstractGradleExecuter {
    private static final Logger LOG = LoggerFactory.getLogger(ForkingGradleExecuter.class);
    private final TestFile gradleHomeDir;

    public ForkingGradleExecuter(TestFile gradleHomeDir) {
        this.gradleHomeDir = gradleHomeDir;
    }

    public TestFile getGradleHomeDir() {
        return gradleHomeDir;
    }

    @Override
    protected ExecutionResult doRun() {
        Map result = doRun(false);
        return new ForkedExecutionResult(result);
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        Map result = doRun(true);
        return new ForkedExecutionFailure(result);
    }

    protected Map doRun(boolean expectFailure) {
        gradleHomeDir.assertIsDir();

        CommandBuilder commandBuilder = OperatingSystem.current().isWindows() ? new WindowsCommandBuilder()
                : new UnixCommandBuilder();

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();

        ExecHandleBuilder builder = new ExecHandleBuilder() {
            @Override
            public File getWorkingDir() {
                // Override this, so that the working directory is not canonicalised. Some int tests require that
                // the working directory is not canonicalised
                return ForkingGradleExecuter.this.getWorkingDir();
            }
        };
        builder.setStandardOutput(outStream);
        builder.setErrorOutput(errStream);
        builder.environment("GRADLE_HOME", "");
        builder.environment("JAVA_HOME", System.getProperty("java.home"));
        builder.environment("GRADLE_OPTS", "-ea");
        builder.environment(getEnvironmentVars());
        builder.workingDir(getWorkingDir());

        commandBuilder.build(builder);

        builder.args(getAllArgs());

        LOG.info(String.format("Execute in %s with: %s %s", builder.getWorkingDir(), builder.getExecutable(),
                builder.getArgs()));

        ExecHandle proc = builder.build();
        int exitValue = proc.start().waitForFinish().getExitValue();

        String output = outStream.toString();
        String error = errStream.toString();
        boolean failed = exitValue != 0;

        LOG.info("OUTPUT: " + output);
        LOG.info("ERROR: " + error);

        if (failed != expectFailure) {
            String message = String.format("Gradle execution %s in %s with: %s %s%nOutput:%n%s%nError:%n%s%n-----%n",
                    expectFailure ? "did not fail" : "failed", builder.getWorkingDir(), builder.getExecutable(),
                    builder.getArgs(), output, error);
            System.out.println(message);
            throw new RuntimeException(message);
        }
        return GUtil.map("output", output, "error", error);
    }

    private interface CommandBuilder {
        void build(ExecHandleBuilder builder);
    }

    private class WindowsCommandBuilder implements CommandBuilder {
        public void build(ExecHandleBuilder builder) {
            String cmd;
            if (getExecutable() != null) {
                cmd = getExecutable().replace('/', File.separatorChar);
            } else {
                cmd = "gradle";
            }
            builder.executable("cmd");
            builder.args("/c", cmd);
            String gradleHome = gradleHomeDir.getAbsolutePath();
            builder.environment("Path", String.format("%s\\bin;%s", gradleHome, System.getenv("Path")));
            builder.environment("GRADLE_EXIT_CONSOLE", "true");
        }
    }

    private class UnixCommandBuilder implements CommandBuilder {
        public void build(ExecHandleBuilder builder) {
            if (getExecutable() != null) {
                builder.executable(String.format("%s/%s", getWorkingDir().getAbsolutePath(), getExecutable()));
            } else {
                builder.executable(String.format("%s/bin/gradle", gradleHomeDir.getAbsolutePath()));
            }
        }
    }

    private static class ForkedExecutionResult extends AbstractExecutionResult {
        private final Map result;
        private final Pattern skippedTaskPattern = Pattern.compile("(:\\w+(:\\w+)*)\\s+((SKIPPED)|(UP-TO-DATE))");
        private final Pattern notSkippedTaskPattern = Pattern.compile("(:\\w+(:\\w+)*)");

        public ForkedExecutionResult(Map result) {
            this.result = result;
        }

        public String getOutput() {
            return result.get("output").toString();
        }

        public String getError() {
            return result.get("error").toString();
        }

        public ExecutionResult assertTasksExecuted(String... taskPaths) {
            throw new UnsupportedOperationException();
        }

        public ExecutionResult assertTasksSkipped(String... taskPaths) {
            List<String> tasks = findTasks(skippedTaskPattern);
            assertThat(tasks, equalTo(Arrays.asList(taskPaths)));
            return this;
        }

        public ExecutionResult assertTasksNotSkipped(String... taskPaths) {
            List<String> tasks = findTasks(notSkippedTaskPattern);
            assertThat(tasks, equalTo(Arrays.asList(taskPaths)));
            return this;
        }

        private List<String> findTasks(Pattern pattern) {
            List<String> tasks = new ArrayList<String>();
            BufferedReader reader = new BufferedReader(new StringReader(getOutput()));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    java.util.regex.Matcher matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        tasks.add(matcher.group(1));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return tasks;
        }
    }

    private static class ForkedExecutionFailure extends ForkedExecutionResult implements ExecutionFailure {
        public ForkedExecutionFailure(Map result) {
            super(result);
        }

        public ExecutionFailure assertHasLineNumber(int lineNumber) {
            assertThat(getError(), containsString(String.format(" line: %d", lineNumber)));
            return this;
        }

        public ExecutionFailure assertHasFileName(String filename) {
            assertThat(getError(), containsLine(startsWith(filename)));
            return this;
        }

        public ExecutionFailure assertHasCause(String description) {
            assertThatCause(startsWith(description));
            return this;
        }

        public ExecutionFailure assertThatCause(final Matcher<String> matcher) {
            assertThat(getError(), containsLine(new BaseMatcher<String>() {
                public boolean matches(Object o) {
                    String str = (String) o;
                    String prefix = "Cause: ";
                    return str.startsWith(prefix) && matcher.matches(str.substring(prefix.length()));
                }

                public void describeTo(Description description) {
                    matcher.describeTo(description);
                }
            }));
            return this;
        }

        public ExecutionFailure assertHasDescription(String context) {
            assertThatDescription(startsWith(context));
            return this;
        }

        public ExecutionFailure assertThatDescription(Matcher<String> matcher) {
            assertThat(getError(), containsLine(matcher));
            return this;
        }
    }
}
