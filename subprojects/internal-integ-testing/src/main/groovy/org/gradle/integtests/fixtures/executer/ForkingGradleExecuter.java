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

package org.gradle.integtests.fixtures.executer;

import org.gradle.api.Action;
import org.gradle.internal.Factory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.JvmOptions;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.fail;

class ForkingGradleExecuter extends AbstractGradleExecuter {

    public ForkingGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
    }

    public void assertCanExecute() throws AssertionError {
        if (!getDistribution().isSupportsSpacesInGradleAndJavaOpts()) {
            Map<String, String> mergedEnvironmentVars = getMergedEnvironmentVars();
            for (String envVarName : Arrays.asList("JAVA_OPTS", "GRADLE_OPTS")) {
                String envVarValue = mergedEnvironmentVars.get(envVarName);
                if (envVarValue == null) {
                    continue;
                }
                for (String arg : JvmOptions.fromString(envVarValue)) {
                    if (arg.contains(" ")) {
                        throw new AssertionError(String.format("Env var %s contains arg with space (%s) which is not supported", envVarName, arg));
                    }
                }
            }
        }
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> args = new ArrayList<String>();
        args.addAll(super.getAllArgs());
        args.add("--stacktrace");
        return args;
    }

    private ExecHandleBuilder createExecHandleBuilder() {
        TestFile gradleHomeDir = getDistribution().getGradleHomeDir();
        if (!gradleHomeDir.isDirectory()) {
            fail(gradleHomeDir + " is not a directory.\n"
                    + "If you are running tests from IDE make sure that gradle tasks that prepare the test image were executed. Last time it was 'intTestImage' task.");
        }

        ExecHandleBuilder builder = new ExecHandleBuilder() {
            @Override
            public File getWorkingDir() {
                // Override this, so that the working directory is not canonicalised. Some int tests require that
                // the working directory is not canonicalised
                return ForkingGradleExecuter.this.getWorkingDir();
            }
        };

        // Clear the user's environment
        builder.environment("GRADLE_HOME", "");
        builder.environment("JAVA_HOME", "");
        builder.environment("GRADLE_OPTS", "");
        builder.environment("JAVA_OPTS", "");

        builder.environment(getMergedEnvironmentVars());
        builder.workingDir(getWorkingDir());
        builder.setStandardInput(getStdin());

        builder.args(getAllArgs());

        ExecHandlerConfigurer configurer = OperatingSystem.current().isWindows() ? new WindowsConfigurer() : new UnixConfigurer();
        configurer.configure(builder);

        getLogger().info(String.format("Execute in %s with: %s %s", builder.getWorkingDir(), builder.getExecutable(), builder.getArgs()));

        return builder;
    }

    @Override
    public GradleHandle doStart() {
        return createGradleHandle(getResultAssertion(), getDefaultCharacterEncoding(), new Factory<ExecHandleBuilder>() {
            public ExecHandleBuilder create() {
                return createExecHandleBuilder();
            }
        }).start();
    }

    protected ForkingGradleHandle createGradleHandle(Action<ExecutionResult> resultAssertion, String encoding, Factory<ExecHandleBuilder> execHandleFactory) {
        return new ForkingGradleHandle(resultAssertion, encoding, execHandleFactory);
    }

    protected ExecutionResult doRun() {
        return start().waitForFinish();
    }

    protected ExecutionFailure doRunWithFailure() {
        return start().waitForFailure();
    }

    @Override
    protected List<String> getGradleOpts() {
        List<String> gradleOpts = new ArrayList<java.lang.String>(super.getGradleOpts());
        for (Map.Entry<String, String> entry : getImplicitJvmSystemProperties().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            gradleOpts.add(String.format("-D%s=%s", key, value));
        }
        gradleOpts.add("-ea");

        //uncomment for debugging
//        gradleOpts.add("-Xdebug");
//        gradleOpts.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005");

        return gradleOpts;
    }

    private interface ExecHandlerConfigurer {
        void configure(ExecHandleBuilder builder);
    }

    private class WindowsConfigurer implements ExecHandlerConfigurer {
        public void configure(ExecHandleBuilder builder) {
            String cmd;
            if (getExecutable() != null) {
                cmd = getExecutable().replace('/', File.separatorChar);
            } else {
                cmd = "gradle";
            }
            builder.executable("cmd");

            List<String> allArgs = builder.getArgs();
            builder.setArgs(Arrays.asList("/c", cmd));
            builder.args(allArgs);

            String gradleHome = getDistribution().getGradleHomeDir().getAbsolutePath();

            // NOTE: Windows uses Path, but allows asking for PATH, and PATH
            //       is set within builder object for some things such
            //       as CommandLineIntegrationTest, try PATH first, and
            //       then revert to default of Path if null
            Object path = builder.getEnvironment().get("PATH");
            if (path == null) {
                path = builder.getEnvironment().get("Path");
            }
            path = String.format("%s\\bin;%s", gradleHome, path);
            builder.environment("PATH", path);
            builder.environment("Path", path);
            builder.environment("GRADLE_EXIT_CONSOLE", "true");
        }
    }

    private class UnixConfigurer implements ExecHandlerConfigurer {
        public void configure(ExecHandleBuilder builder) {
            if (getExecutable() != null) {
                File exe = new File(getExecutable());
                if (exe.isAbsolute()) {
                    builder.executable(exe.getAbsolutePath());
                } else {
                    builder.executable(String.format("%s/%s", getWorkingDir().getAbsolutePath(), getExecutable()));
                }
            } else {
                builder.executable(String.format("%s/bin/gradle", getDistribution().getGradleHomeDir().getAbsolutePath()));
            }
        }
    }

}
