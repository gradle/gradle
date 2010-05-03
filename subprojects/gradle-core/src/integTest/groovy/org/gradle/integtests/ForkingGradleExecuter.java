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
package org.gradle.integtests;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.util.GUtil;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.util.Matchers.containsLine;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

// todo: implement more of the unsupported methods
public class ForkingGradleExecuter extends AbstractGradleExecuter {
    private static final Logger LOG = LoggerFactory.getLogger(ForkingGradleExecuter.class);
    private final GradleDistribution distribution;
    private Map<String, String> environmentVars = new HashMap<String, String>();
    private String command;

    public ForkingGradleExecuter(GradleDistribution distribution) {
        this.distribution = distribution;
        inDirectory(distribution.getTestDir());
    }

    public GradleExecuter withEnvironmentVars(Map<String, ?> environment) {
        environmentVars.clear();
        for (Map.Entry<String, ?> entry : environment.entrySet()) {
            environmentVars.put(entry.getKey(), entry.getValue().toString());
        }
        return this;
    }

    public GradleExecuter usingExecutable(String script) {
        command = script;
        return this;
    }

    public ExecutionResult run() {
        Map result = doRun(false);
        return new ForkedExecutionResult(result);
    }

    public ExecutionFailure runWithFailure() {
        Map result = doRun(true);
        return new ForkedExecutionFailure(result);
    }

    private Map doRun(boolean expectFailure) {
        String windowsCmd;
        String unixCmd;
        if (command != null) {
            windowsCmd = command.replace('/', File.separatorChar);
            unixCmd = String.format("%s/%s", getWorkingDir().getAbsolutePath(), command);
        } else {
            windowsCmd = "gradle";
            unixCmd = String.format("%s/bin/gradle", distribution.getGradleHomeDir().getAbsolutePath());
        }
        return executeInternal(windowsCmd, unixCmd, expectFailure);
    }

    @Override
    protected List<String> getAllArgs() {
        return GUtil.addLists(getExtraArgs(), super.getAllArgs());
    }

    private List<String> getExtraArgs() {
        List<String> args = new ArrayList<String>();

        if (!isDisableTestGradleUserHome()) {
            args.add("--gradle-user-home");
            args.add(distribution.getUserHomeDir().getAbsolutePath());
        }

        return args;
    }

    Map executeInternal(String windowsCommandSnippet, String unixCommandSnippet, boolean expectFailure) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        String gradleHome = distribution.getGradleHomeDir().toString();

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
        builder.environment(environmentVars);
        builder.workingDir(getWorkingDir());

        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            builder.executable("cmd");
            builder.args("/c", windowsCommandSnippet);
            builder.environment("Path", String.format("%s\\bin;%s", gradleHome, System.getenv("Path")));
            builder.environment("GRADLE_EXIT_CONSOLE", "true");
        } else {
            builder.executable(unixCommandSnippet);
        }

        builder.setArgs(getAllArgs());

        LOG.info(String.format("Execute in %s with: %s %s", builder.getWorkingDir(), builder.getExecutable(),
                builder.getArgs()));

        ExecHandle proc = builder.build();
        proc.start().waitForFinish();

        int exitValue = proc.getExitCode();
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

    private static class ForkedExecutionResult implements ExecutionResult {
        private final Map result;

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
            throw new UnsupportedOperationException();
        }
    }

    private static class ForkedExecutionFailure extends ForkedExecutionResult implements ExecutionFailure {
        private final Map result;

        public ForkedExecutionFailure(Map result) {
            super(result);
            this.result = result;
        }

        public void assertHasLineNumber(int lineNumber) {
            assertThat(getError(), containsString(String.format(" line: %d", lineNumber)));
        }

        public void assertHasFileName(String filename) {
            assertThat(getError(), containsLine(startsWith(filename)));
        }

        public void assertHasCause(String description) {
            assertThatCause(startsWith(description));
        }

        public void assertThatCause(final Matcher<String> matcher) {
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
        }

        public void assertHasDescription(String context) {
            assertThatDescription(startsWith(context));
        }

        public void assertThatDescription(Matcher<String> matcher) {
            assertThat(getError(), containsLine(matcher));
        }
    }
}
