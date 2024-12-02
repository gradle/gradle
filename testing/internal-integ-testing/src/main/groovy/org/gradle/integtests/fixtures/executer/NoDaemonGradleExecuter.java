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
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.Factory;
import org.gradle.internal.jvm.JpmsConfiguration;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.internal.BaseExecHandleBuilder;
import org.gradle.process.internal.ClientExecHandleBuilder;
import org.gradle.process.internal.DefaultClientExecHandleBuilder;
import org.gradle.process.internal.JvmOptions;
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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
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
    protected boolean isSingleUseDaemonRequested() {
        if (!requireDaemon) {
            return false;
        }
        CliDaemonArgument cliDaemonArgument = resolveCliDaemonArgument();
        return cliDaemonArgument == CliDaemonArgument.NOT_DEFINED || cliDaemonArgument == CliDaemonArgument.NO_DAEMON;
    }

    @Override
    public void assertCanExecute() throws AssertionError {
        if (!getDistribution().isSupportsSpacesInGradleAndJavaOpts()) {
            Map<String, String> environmentVars = buildInvocation().environmentVars;
            for (String envVarName : Arrays.asList("JAVA_OPTS", "GRADLE_OPTS")) {
                String envVarValue = environmentVars.get(envVarName);
                if (envVarValue == null) {
                    continue;
                }
                for (String arg : JvmOptions.fromString(envVarValue)) {
                    if (arg.contains(" ")) {
                        throw new AssertionError(String.format("Env var %s contains arg with space (%s) which is not supported by Gradle %s", envVarName, arg, getDistribution().getVersion().getVersion()));
                    }
                }
            }
        }
    }

    @Override
    protected void transformInvocation(GradleInvocation invocation) {
        if (!invocation.buildJvmArgs.isEmpty() && !isUseDaemon() && !isSingleUseDaemonRequested()) {
            // Ensure the arguments match between the launcher and the expected daemon args
            String quotedArgs = joinAndQuoteJvmArgs(invocation.buildJvmArgs);
            invocation.implicitLauncherJvmArgs.add("-Dorg.gradle.jvmargs=" + quotedArgs);
        }

        if (getDistribution().isSupportsSpacesInGradleAndJavaOpts()) {
            // Mix the implicit launcher JVM args in with the requested JVM args
            super.transformInvocation(invocation);
        } else {
            // Need to move those implicit JVM args that contain a space to the Gradle command-line (if possible)
            // Note that this isn't strictly correct as some system properties can only be set on JVM start up.
            // Should change the implementation to deal with these properly
            for (String jvmArg : invocation.implicitLauncherJvmArgs) {
                if (!jvmArg.contains(" ")) {
                    invocation.launcherJvmArgs.add(jvmArg);
                } else if (jvmArg.startsWith("-D")) {
                    invocation.args.add(jvmArg);
                } else {
                    throw new UnsupportedOperationException(String.format("Cannot handle launcher JVM arg '%s' as it contains whitespace. This is not supported by Gradle %s.",
                        jvmArg, getDistribution().getVersion().getVersion()));
                }
            }
        }
        invocation.implicitLauncherJvmArgs.clear();

        // Inject the launcher JVM args via one of the environment variables
        Map<String, String> environmentVars = invocation.environmentVars;
        String jvmOptsEnvVar;
        if (!environmentVars.containsKey("GRADLE_OPTS")) {
            jvmOptsEnvVar = "GRADLE_OPTS";
        } else if (!environmentVars.containsKey("JAVA_OPTS")) {
            jvmOptsEnvVar = "JAVA_OPTS";
        } else {
            // This could be handled, just not implemented yet
            throw new UnsupportedOperationException(String.format("Both GRADLE_OPTS and JAVA_OPTS environment variables are being used. Cannot provide JVM args %s to Gradle command.", invocation.launcherJvmArgs));
        }
        final String value = toJvmArgsString(invocation.launcherJvmArgs);
        environmentVars.put(jvmOptsEnvVar, value);

        // Always set JAVA_HOME, so the daemon process runs on the configured JVM
        environmentVars.put("JAVA_HOME", getJavaHome());
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> args = new ArrayList<>(super.getAllArgs());
        addPropagatedSystemProperties(args);
        if(!isQuiet() && isAllowExtraLogging()) {
            if (!containsLoggingArgument(args)) {
                args.add(0, "-i");
            }
        }

        // Workaround for https://issues.gradle.org/browse/GRADLE-2625
        if (getUserHomeDir() != null) {
            args.add(String.format("-Duser.home=%s", getUserHomeDir().getPath()));
        }

        return args;
    }

    private boolean containsLoggingArgument(List<String> args) {
        for (String logArg : asList("-i", "--info", "-d", "--debug", "-w", "--warn", "-q", "--quiet")) {
            if (args.contains(logArg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<String> getImplicitBuildJvmArgs() {
        List<String> buildJvmOptions = super.getImplicitBuildJvmArgs();
        if (!isUseDaemon() && !isSingleUseDaemonRequested() && getJavaVersionFromJavaHome().isJava9Compatible()) {
            buildJvmOptions.addAll(JpmsConfiguration.GRADLE_DAEMON_JPMS_ARGS);
        }
        return buildJvmOptions;
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
        return getJavaVersionFromJavaHome().isJava7Compatible();
    }

    @Override
    protected GradleHandle createGradleHandle() {
        return createForkingGradleHandle(getResultAssertion(), getDefaultCharacterEncoding(), getExecHandleFactory()).start();
    }

    protected Factory<BaseExecHandleBuilder> getExecHandleFactory() {
        return new Factory<BaseExecHandleBuilder>() {
            @Override
            public BaseExecHandleBuilder create() {
                TestFile gradleHomeDir = getDistribution().getGradleHomeDir();
                if (gradleHomeDir != null && !gradleHomeDir.isDirectory()) {
                    fail(gradleHomeDir + " is not a directory.\n"
                        + "The test is most likely not written in a way that it can run with the embedded executer.");
                }

                NativeServicesTestFixture.initialize();
                DefaultClientExecHandleBuilder builder = new DefaultClientExecHandleBuilder(TestFiles.pathToFileResolver(), Executors.newCachedThreadPool(), new DefaultBuildCancellationToken()) {
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
                builder.environment("GRADLE_OPTS", "");
                builder.environment("JAVA_OPTS", "");
                builder.environment(ArtifactCachesProvider.READONLY_CACHE_ENV_VAR, "");

                GradleInvocation invocation = buildInvocation();

                builder.environment(invocation.environmentVars);
                builder.setWorkingDir(getWorkingDir());
                builder.setStandardInput(connectStdIn());

                builder.args(invocation.args);

                ExecHandlerConfigurer configurer = OperatingSystem.current().isWindows() ? new WindowsConfigurer() : new UnixConfigurer();
                configurer.configure(builder);
                getLogger().debug(String.format("Execute in %s with: %s %s", builder.getWorkingDir(), builder.getExecutable(), builder.getArgs()));
                return builder;
            }
        };
    }

    protected ForkingGradleHandle createForkingGradleHandle(Action<ExecutionResult> resultAssertion, String encoding, Factory<BaseExecHandleBuilder> execHandleFactory) {
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
        void configure(ClientExecHandleBuilder builder);
    }

    private class WindowsConfigurer implements ExecHandlerConfigurer {
        @Override
        public void configure(ClientExecHandleBuilder builder) {
            String cmd;
            if (getExecutable() != null) {
                cmd = getExecutable().replace('/', File.separatorChar);
            } else {
                cmd = "gradle";
            }
            builder.setExecutable("cmd.exe");

            List<String> allArgs = builder.getArgs();
            String actualCommand = quote(quote(cmd) + " " + allArgs.stream().map(NoDaemonGradleExecuter::quote).collect(joining(" ")));
            builder.setArgs(Arrays.asList("/d", "/c", actualCommand));

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

    private static String quote(String arg) {
        if(arg.isEmpty()){
            return "\"\"";
        }
        if (arg.contains(" ")) {
            return "\"" + arg + "\"";

        }
        return arg;
    }

    private class UnixConfigurer implements ExecHandlerConfigurer {
        @Override
        public void configure(ClientExecHandleBuilder builder) {
            if (getExecutable() != null) {
                File exe = new File(getExecutable());
                if (exe.isAbsolute()) {
                    builder.setExecutable(exe.getAbsolutePath());
                } else {
                    builder.setExecutable(String.format("%s/%s", getWorkingDir().getAbsolutePath(), getExecutable()));
                }
            } else {
                builder.setExecutable(String.format("%s/bin/gradle", getDistribution().getGradleHomeDir().getAbsolutePath()));
            }
        }
    }

}
