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

import org.gradle.api.JavaVersion;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.util.GradleVersion;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.collections.CollectionUtils.containsAny;

public class DaemonGradleExecuter extends ForkingGradleExecuter {
    private static final JvmVersionDetector JVM_VERSION_DETECTOR = GLOBAL_SERVICES.get(JvmVersionDetector.class);

    public DaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
        requireDaemon();
    }

    public DaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion gradleVersion, IntegrationTestBuildContext buildContext) {
        super(distribution, testDirectoryProvider, gradleVersion, buildContext);
        requireDaemon();
    }

    @Override
    protected void validateDaemonVisibility() {
        // Ignore. Should really ignore only when daemon has not been explicitly enabled or disabled
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> args = new ArrayList<String>(super.getAllArgs());
        if(!isQuiet() && isAllowExtraLogging()) {
            if (!containsAny(args, asList("-i", "--info", "-d", "--debug", "-q", "--quiet"))) {
                args.add(0, "-i");
            }
        }

        // Workaround for https://issues.gradle.org/browse/GRADLE-2625
        if (getUserHomeDir() != null) {
            args.add(String.format("-Duser.home=%s", getUserHomeDir().getPath()));
        }

        return args;
    }

    @Override
    protected List<String> getImplicitBuildJvmArgs() {
        if (!isUseDaemon() || !isSharedDaemons()) {
            return super.getImplicitBuildJvmArgs();
        }

        // Add JVM heap settings only for shared daemons
        List<String> buildJvmOpts = new ArrayList<String>(super.getImplicitBuildJvmArgs());

        if (JVM_VERSION_DETECTOR.getJavaVersion(Jvm.forHome(getJavaHome())).compareTo(JavaVersion.VERSION_1_8) < 0) {
            buildJvmOpts.add("-XX:MaxPermSize=320m");
        }

        buildJvmOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        buildJvmOpts.add("-XX:HeapDumpPath=" + buildContext.getGradleUserHomeDir().getAbsolutePath());
        return buildJvmOpts;
    }

    @Override
    protected void transformInvocation(GradleInvocation invocation) {
        super.transformInvocation(invocation);

        if (!noExplicitNativeServicesDir) {
            invocation.environmentVars.put(NativeServices.NATIVE_DIR_OVERRIDE, buildContext.getNativeServicesDir().getAbsolutePath());
        }
    }
}
