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
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.CommandLineParserFactory;
import org.gradle.cli.SystemPropertiesCommandLineConverter;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.os.OperatingSystem;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.ExecHandleState;
import org.gradle.util.TestFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    public DaemonRegistry getDaemonRegistry() {
        File userHome = getUserHomeDir();
        if (userHome == null) {
            userHome = StartParameter.DEFAULT_GRADLE_USER_HOME;
        }

        File daemonBaseDir = DaemonDir.calculateDirectoryViaPropertiesOrUseDefaultInGradleUserHome(getSystemPropertiesFromArgs(), userHome);
        return new DaemonRegistryServices(daemonBaseDir).get(DaemonRegistry.class);
    }

    protected Map<String, String> getSystemPropertiesFromArgs() {
        SystemPropertiesCommandLineConverter converter = new SystemPropertiesCommandLineConverter();
        converter.setCommandLineParserFactory(new CommandLineParserFactory() {
            public CommandLineParser create() {
                return new CommandLineParser().allowUnknownOptions();
            }
        }); 
        
        return converter.convert(getAllArgs());
        
    }
    /**
     * Adds some options to the GRADLE_OPTS environment variable to use.
     */
    public void addGradleOpts(String... opts) {
        gradleOpts.addAll(Arrays.asList(opts));
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> args = new ArrayList<String>();
        args.addAll(super.getAllArgs());
        args.add("--stacktrace");
        return args;
    }

    private ExecHandleBuilder createExecHandleBuilder() {
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
        builder.environment("JAVA_HOME", getJavaHome());
        builder.environment("GRADLE_OPTS", formatGradleOpts());
        builder.environment(getAllEnvironmentVars());
        builder.workingDir(getWorkingDir());
        builder.setStandardInput(getStdin());

        commandBuilder.build(builder);

        builder.args(getAllArgs());

        LOG.info(String.format("Execute in %s with: %s %s", builder.getWorkingDir(), builder.getExecutable(),
                builder.getArgs()));

        return builder;
    }

    public GradleHandle createHandle() {
        return new ForkingGradleHandle(this);
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

    private static class MultiplexingOutputStream extends OutputStream {
        final OutputStream systemOut;
        final OutputStream nonSystemOut;

        public MultiplexingOutputStream(OutputStream systemOut, OutputStream nonSystemOut) {
            this.systemOut = systemOut;
            this.nonSystemOut = nonSystemOut;
        }

        public void write(int b) throws IOException {
            nonSystemOut.write(b);
            systemOut.write(b);
        }
    }

    private static class ForkingGradleHandle extends OutputScrapingGradleHandle {
        private static final Logger LOG = LoggerFactory.getLogger(ForkingGradleHandle.class);

        final private ForkingGradleExecuter executer;

        final private ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        final private ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();

        private ExecHandle execHandle;

        public ForkingGradleHandle(ForkingGradleExecuter executer) {
            this.executer = executer;
        }

        public ForkingGradleExecuter getExecuter() {
            return executer;
        }

        public String getStandardOutput() {
            return standardOutput.toString();
        }

        public String getErrorOutput() {
            return errorOutput.toString();
        }

        public GradleHandle start() {
            createExecHandle().start();
            return this;
        }

        public GradleHandle abort() {
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

            boolean didFail = execResult.getExitValue() != 0;
            if (didFail != expectFailure) {
                String message = String.format("Gradle execution %s in %s with: %s %nError:%n%s%n-----%n",
                        expectFailure ? "did not fail" : "failed", execHandle.getDirectory(), execHandle.getCommand(), error);
                throw new RuntimeException(message);
            }

            return expectFailure ? toExecutionFailure(output, error) : toExecutionResult(output, error);
        }
    }

}
