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

import org.gradle.os.OperatingSystem;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.util.Jvm;
import org.gradle.util.TestFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

    @Override
    protected ExecutionResult doRun() {
        Map<String, String> result = doRun(false);
        return new OutputScrapingExecutionResult(result.get("output"), result.get("error"));
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        Map<String, String> result = doRun(true);
        return new OutputScrapingExecutionFailure(result.get("output"), result.get("error"));
    }

    /**
     * Adds some options to the GRADLE_OPTS environment variable to use.
     */
    public void addGradleOpts(String... opts) {
        gradleOpts.addAll(Arrays.asList(opts));
    }

    protected Map<String, String> doRun(boolean expectFailure) {
        if (!gradleHomeDir.isDirectory()) {
            fail(gradleHomeDir + " is not a directory.\n"
                    + "If you are running tests from IDE make sure that gradle tasks that prepare the test image were executed. Last time it was 'intTestImage' task.");
        }

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
        builder.environment("JAVA_HOME", Jvm.current().getJavaHome());
        builder.environment("GRADLE_OPTS", formatGradleOpts());
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
        
        Map<String, String> map = new HashMap<String, String>(2);
        map.put("output", output);
        map.put("error", error);
        return map;
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

}
