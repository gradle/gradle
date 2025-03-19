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

import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.util.GradleVersion;

import java.lang.management.ManagementFactory;

import static org.gradle.integtests.fixtures.executer.AbstractGradleExecuter.CliDaemonArgument.NO_DAEMON;

public class DaemonGradleExecuter extends NoDaemonGradleExecuter {

    private boolean daemonExplicitlyRequired;
    private boolean debugMode = false;

    public DaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
        super.requireDaemon();
    }

    public DaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion gradleVersion, IntegrationTestBuildContext buildContext) {
        super(distribution, testDirectoryProvider, gradleVersion, buildContext);
        debugMode = isDebuggerAttached();
        super.requireDaemon();
    }

    @Override
    protected boolean isSingleUseDaemonRequested() {
        return resolveCliDaemonArgument() == NO_DAEMON && requireDaemon;
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
    protected void transformInvocation(GradleInvocation invocation) {
        super.transformInvocation(invocation);

        if (!noExplicitNativeServicesDir) {
            invocation.environmentVars.put(NativeServices.NATIVE_DIR_OVERRIDE, buildContext.getNativeServicesDir().getAbsolutePath());
        }
    }

    @Override
    public GradleExecuter reset() {
        super.reset();
        if (debugMode) {
            startBuildProcessInDebugger(true);
        }
        return this;
    }

    protected boolean isDebuggerAttached() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;
    }
}
