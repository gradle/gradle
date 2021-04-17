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

import org.gradle.integtests.fixtures.FileSystemWatchingHelper;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.util.GradleVersion;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class DaemonGradleExecuter extends NoDaemonGradleExecuter {

    private boolean daemonExplicitlyRequired;

    public DaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
        super.requireDaemon();
    }

    public DaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion gradleVersion, IntegrationTestBuildContext buildContext) {
        super(distribution, testDirectoryProvider, gradleVersion, buildContext);
        super.requireDaemon();
        waitForChangesToBePickedUpBeforeExecution();
    }

    private void waitForChangesToBePickedUpBeforeExecution() {
        // File system watching is now on by default, so we need to wait for changes to be picked up before each execution.
        beforeExecute(executer -> {
            try {
                FileSystemWatchingHelper.waitForChangesToBePickedUp();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        });
    }

    @Override
    public GradleExecuter requireDaemon() {
        daemonExplicitlyRequired = true;
        return super.requireDaemon();
    }

    @Override
    protected void validateDaemonVisibility() {
        if (isDaemonExplicitlyRequired()) {
            super.validateDaemonVisibility();
        }
    }

    protected boolean isDaemonExplicitlyRequired() {
        return daemonExplicitlyRequired || resolveCliDaemonArgument() == CliDaemonArgument.DAEMON;
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> args = new ArrayList<String>(super.getAllArgs());
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
    protected void transformInvocation(GradleInvocation invocation) {
        super.transformInvocation(invocation);

        if (!noExplicitNativeServicesDir) {
            invocation.environmentVars.put(NativeServices.NATIVE_DIR_OVERRIDE, buildContext.getNativeServicesDir().getAbsolutePath());
        }
    }
}
