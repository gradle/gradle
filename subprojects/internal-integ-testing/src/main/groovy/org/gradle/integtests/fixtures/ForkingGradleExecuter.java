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

import org.gradle.StartParameter;
import org.gradle.os.OperatingSystem;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.ExecHandleState;
import org.gradle.util.Jvm;
import org.gradle.util.TestFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.fail;

public class ForkingGradleExecuter extends AbstractGradleExecuter {
    private static final Logger LOG = LoggerFactory.getLogger(ForkingGradleExecuter.class);
    private final TestFile gradleHomeDir;
    private final List<String> gradleOpts = new ArrayList<String>();

    public ForkingGradleExecuter(TestFile gradleHomeDir) {
        this.gradleHomeDir = gradleHomeDir;
        gradleOpts.add("-ea");
    }

    public TestFile getGradleHomeDir() {
        return gradleHomeDir;
    }

    public DaemonController getDaemonController() {
        File userHome = getUserHomeDir();
        if (userHome == null) {
            userHome = StartParameter.DEFAULT_GRADLE_USER_HOME;
        }

        return new RegistryBackedDaemonController(userHome);
    }

    /**
     * Adds some options to the GRADLE_OPTS environment variable to use.
     */
    public void addGradleOpts(String... opts) {
        gradleOpts.addAll(Arrays.asList(opts));
    }

    public ExecHandleBuilder createExecHandleBuilder() {
        if (!gradleHomeDir.isDirectory()) {
            fail(gradleHomeDir + " is not a directory.\n"
                    + "If you are running tests from IDE make sure that gradle tasks that prepare the test image were executed. Last time it was 'intTestImage' task.");
        }

        CommandBuilder commandBuilder = OperatingSystem.current().isWindows() ? new WindowsCommandBuilder()
                : new UnixCommandBuilder();

        ExecHandleBuilder builder = new ExecHandleBuilder() {
            @Override
            public File getWorkingDir() {
                // Override this, so that the working directory is not canonicalised. Some int tests require that
                // the working directory is not canonicalised
                return ForkingGradleExecuter.this.getWorkingDir();
            }
        };
        builder.environment("GRADLE_HOME", "");
        builder.environment("JAVA_HOME", Jvm.current().getJavaHome());
        builder.environment("GRADLE_OPTS", formatGradleOpts());
        builder.environment(getEnvironmentVars());
        builder.workingDir(getWorkingDir());
        builder.setStandardInput(getStdin());

        commandBuilder.build(builder);

        builder.args(getAllArgs());

        LOG.info(String.format("Execute in %s with: %s %s", builder.getWorkingDir(), builder.getExecutable(),
                builder.getArgs()));

        return builder;
    }

    public GradleHandle<? extends ForkingGradleExecuter> createHandle() {
        return new ForkingGradleHandle<ForkingGradleExecuter>(this);
    }

    protected ExecutionResult doRun() {
        return createHandle().start().waitForFinish();
    }

    protected ExecutionFailure doRunWithFailure() {
        return createHandle().start().waitForFailure();
    }

    private String formatGradleOpts() {
        StringBuilder result = new StringBuilder();
        for (String gradleOpt : gradleOpts) {
            if (result.length() > 0) {
                result.append(" ");
            }
            if (gradleOpt.contains(" ")) {
                assert !gradleOpt.contains("\"");
                result.append('"');
                result.append(gradleOpt);
                result.append('"');
            } else {
                result.append(gradleOpt);
            }
        }
        return result.toString();
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

            // NOTE: Windows uses Path, but allows asking for PATH, and PATH
            //       is set within builder object for some things such
            //       as CommandLineIntegrationTest, try PATH first, and
            //       then revert to default of Path if null
            Object path = builder.getEnvironment().get("PATH");
            if (path == null) {
                path = builder.getEnvironment().get("Path");
            }
            builder.environment("Path", String.format("%s\\bin;%s",
                                                      gradleHome,
                                                      path));
            builder.environment("GRADLE_EXIT_CONSOLE", "true");
        }
    }

    private class UnixCommandBuilder implements CommandBuilder {
        public void build(ExecHandleBuilder builder) {
            if (getExecutable() != null) {
                File exe = new File(getExecutable());
                if (exe.isAbsolute()) {
                    builder.executable(exe.getAbsolutePath());
                } else {
                    builder.executable(String.format("%s/%s", getWorkingDir().getAbsolutePath(), getExecutable()));
                }
            } else {
                builder.executable(String.format("%s/bin/gradle", gradleHomeDir.getAbsolutePath()));
            }
        }
    }

    protected static class ForkingGradleHandle<T extends ForkingGradleExecuter> extends OutputScrapingGradleHandle<T> {
        private static final Logger LOG = LoggerFactory.getLogger(ForkingGradleHandle.class);

        final private T executer;
        private boolean passthrough;

        private class MultiplexingOutputStream extends OutputStream {
            OutputStream systemOut;
            OutputStream nonSystemOut;

            public MultiplexingOutputStream(OutputStream systemOut, OutputStream nonSystemOut) {
                this.systemOut = systemOut;
                this.nonSystemOut = nonSystemOut;
            }

            public void write(int b) throws IOException {
                nonSystemOut.write(b);
                if (passthrough) {
                    systemOut.write(b);
                }
            }
        }


        final private ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        final private ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        private InputStream inputStream;

        private ExecHandle execHandle;

        public ForkingGradleHandle(T executer) {
            this.executer = executer;
        }

        public T getExecuter() {
            return executer;
        }

        public GradleHandle<T> passthroughOutput() {
            passthrough = true;
            return this;
        }

        public String getStandardOutput() {
            return standardOutput.toString();
        }

        public String getErrorOutput() {
            return errorOutput.toString();
        }

        public GradleHandle<T> setStandardInput(InputStream inputStream) {
            this.inputStream = inputStream;
            return this;
        }

        public GradleHandle<T> start() {
            createExecHandle().start();
            return this;
        }

        public GradleHandle<T> abort() {
            getExecHandle().abort();
            return this;
        }

        public boolean isRunning() {
            return execHandle != null && execHandle.getState() == ExecHandleState.STARTED;
        }

        protected ExecHandle getExecHandle() {
            if (execHandle == null) {
                throw new IllegalStateException("you must call start() before calling this method");
            }

            return execHandle;
        }

        protected ExecHandle createExecHandle() {
            if (execHandle != null) {
                throw new IllegalStateException("you have already called start() on this handle");
            }

            ExecHandleBuilder execBuilder = getExecuter().createExecHandleBuilder();
            execBuilder.setStandardOutput(new MultiplexingOutputStream(System.out, standardOutput));
            execBuilder.setErrorOutput(new MultiplexingOutputStream(System.err, errorOutput));
            if (inputStream != null) {
                execBuilder.setStandardInput(inputStream);
            }
            execHandle = execBuilder.build();

            return execHandle;
        }

        public ExecutionResult waitForFinish() {
            return waitForStop(false);
        }

        public ExecutionFailure waitForFailure() {
            return (ExecutionFailure)waitForStop(true);
        }

        protected ExecutionResult waitForStop(boolean expectFailure) {
            ExecHandle execHandle = getExecHandle();
            ExecResult execResult = execHandle.waitForFinish();
            execResult.rethrowFailure(); // nop if all ok

            String output = getStandardOutput();
            String error = getErrorOutput();

            LOG.info("OUTPUT: " + output);
            LOG.info("ERROR: " + error);

            boolean didFail = execResult.getExitValue() != 0;
            if (didFail != expectFailure) {
                String message = String.format("Gradle execution %s in %s with: %s %nOutput:%n%s%nError:%n%s%n-----%n",
                        expectFailure ? "did not fail" : "failed", execHandle.getDirectory(), execHandle.getCommand(), output, error);
                System.out.println(message);
                throw new RuntimeException(message);
            }

            return expectFailure ? toExecutionFailure(output, error) : toExecutionResult(output, error);
        }
    }

}
