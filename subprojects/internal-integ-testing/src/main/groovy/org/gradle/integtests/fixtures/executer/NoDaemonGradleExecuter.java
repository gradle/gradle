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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.internal.Factory;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.internal.AbstractExecHandleBuilder;
import org.gradle.process.internal.DefaultExecHandleBuilder;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.Assert.fail;

public class NoDaemonGradleExecuter extends AbstractGradleExecuter {

    public NoDaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
    }

    public NoDaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion version) {
        super(distribution, testDirectoryProvider, version);
    }

    public NoDaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion gradleVersion, IntegrationTestBuildContext buildContext) {
        super(distribution, testDirectoryProvider, gradleVersion, buildContext);
    }

    @Override
    public void assertCanExecute() throws AssertionError {
    }

    @Override
    protected void transformInvocation(GradleInvocation invocation) {
        // Mix the implicit launcher JVM args in with the requested JVM args
        super.transformInvocation(invocation);

        // Inject the launcher JVM args via one of the environment variables
        Map<String, String> environmentVars = invocation.environmentVars;

        // Add a JAVA_HOME if none provided
        if (!environmentVars.containsKey("JAVA_HOME")) {
            environmentVars.put("JAVA_HOME", getJavaHome().getAbsolutePath());
        }
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> args = new ArrayList<String>();
        args.addAll(super.getAllArgs());
        addPropagatedSystemProperties(args);

        // Workaround for https://issues.gradle.org/browse/GRADLE-2625
        if (getUserHomeDir() != null) {
            args.add(String.format("-Duser.home=%s", getUserHomeDir().getPath()));
        }

        return args;
    }

    private void addPropagatedSystemProperties(List<String> args) {
        for (String propName : PROPAGATED_SYSTEM_PROPERTIES) {
            String propValue = System.getProperty(propName);
            if (propValue != null) {
                args.add("-D" + propName + "=" + propValue);
            }
        }
    }

    @Override
    protected boolean supportsWhiteSpaceInEnvVars() {
        final Jvm current = Jvm.current();
        if (getJavaHome().equals(current.getJavaHome())) {
            // we can tell for sure
            return current.getJavaVersion().isJava7Compatible();
        } else {
            // TODO improve lookup by reusing AvailableJavaHomes testfixture
            // for now we play it safe and just return false;
            return false;
        }
    }

    @Override
    protected GradleHandle createGradleHandle() {
        return createForkingGradleHandle(getResultAssertion(), getDefaultCharacterEncoding(), getExecHandleFactory()).start();
    }

    protected Factory<? extends AbstractExecHandleBuilder> getExecHandleFactory() {
        return new Factory<DefaultExecHandleBuilder>() {
            @Override
            public DefaultExecHandleBuilder create() {
                TestFile gradleHomeDir = getDistribution().getGradleHomeDir();
                if (gradleHomeDir != null && !gradleHomeDir.isDirectory()) {
                    fail(gradleHomeDir + " is not a directory.\n"
                        + "The test is most likely not written in a way that it can run with the embedded executer.");
                }

                NativeServicesTestFixture.initialize();
                DefaultExecHandleBuilder builder = new DefaultExecHandleBuilder(TestFiles.pathToFileResolver(), Executors.newCachedThreadPool()) {
                    @Override
                    public File getWorkingDir() {
                        // Override this, so that the working directory is not canonicalised. Some int tests require that
                        // the working directory is not canonicalised
                        return NoDaemonGradleExecuter.this.getWorkingDir();
                    }
                };

                // Clear the user's environment
                builder.environment("GRADLE_HOME", "");
                builder.environment("JAVA_HOME", "");
//                builder.environment("GRADLE_OPTS", "");
                builder.environment("JAVA_OPTS", "");
                builder.environment(ArtifactCachesProvider.READONLY_CACHE_ENV_VAR, "");

                GradleInvocation invocation = buildInvocation();

                builder.environment(invocation.environmentVars);
                builder.workingDir(getWorkingDir());
                builder.setStandardInput(connectStdIn());

                builder.args(invocation.args);

                ExecHandlerConfigurer configurer = OperatingSystem.current().isWindows() ? new WindowsConfigurer() : new UnixConfigurer();
                configurer.configure(builder);
                getLogger().debug(String.format("Execute in %s with: %s %s", builder.getWorkingDir(), builder.getExecutable(), builder.getArgs()));
                return builder;
            }
        };
    }

    protected ForkingGradleHandle createForkingGradleHandle(Action<ExecutionResult> resultAssertion, String encoding, Factory<? extends AbstractExecHandleBuilder> execHandleFactory) {
        return new ForkingGradleHandle(getStdinPipe(), isUseDaemon(), resultAssertion, encoding, execHandleFactory, getDurationMeasurement());
    }

    @Override
    protected ExecutionResult doRun() {
        return createGradleHandle().waitForFinish();
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        return createGradleHandle().waitForFailure();
    }

    private interface ExecHandlerConfigurer {
        void configure(ExecHandleBuilder builder);
    }

    private class WindowsConfigurer implements ExecHandlerConfigurer {
        @Override
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
        }
    }

    private class UnixConfigurer implements ExecHandlerConfigurer {
        @Override
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
