/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.extensions.AbstractMultiTestInterceptor
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.GradleVersion
import org.spockframework.runtime.extension.IMethodInvocation

class ToolingApiExecution extends AbstractMultiTestInterceptor.Execution {

    private static final GradleVersion INSTALLATION_GRADLE_VERSION

    static  {
        // If we are testing a non-current tooling API version, we will have loaded the class using its classloader and thus
        // GradleVersion.current() will report that version. In order to set up the testing infrastructure, we need to
        // know the version of Gradle being built
        def currentVersionOverride = System.getProperty("org.gradle.integtest.currentVersion")
        if (currentVersionOverride != null) {
            INSTALLATION_GRADLE_VERSION = GradleVersion.version(currentVersionOverride)
        } else {
            INSTALLATION_GRADLE_VERSION = GradleVersion.current()
        }
    }

    final GradleDistribution toolingApi
    final GradleDistribution gradle

    private final GradleVersion toolingApiVersion
    private final GradleVersion gradleVersion

    ToolingApiExecution(GradleDistribution loadedDistribution, GradleDistribution packagedDistribution) {
        if (isClassloadedVersionCurrent()) {
            // Gradle current -> TAPI {source}
            this.gradle = packagedDistribution
            this.toolingApi = loadedDistribution
        } else {
            // TAPI {target} -> Gradle current
            this.gradle = loadedDistribution
            this.toolingApi = packagedDistribution
        }
        this.toolingApiVersion = GradleVersion.version(toolingApi.version.version)
        this.gradleVersion = GradleVersion.version(gradle.version.version)
    }

    private static boolean isClassloadedVersionCurrent() {
        return INSTALLATION_GRADLE_VERSION == GradleVersion.current()
    }

    @Override
    protected String getDisplayName() {
        return "TAPI ${displayName(toolingApiVersion)} -> Gradle ${displayName(gradleVersion)}"
    }

    @Override
    String toString() {
        return displayName
    }

    private static String displayName(GradleVersion version) {
        if (version == INSTALLATION_GRADLE_VERSION) {
            return "current"
        }
        return version.version
    }

    @Override
    boolean isTestEnabled(AbstractMultiTestInterceptor.TestDetails testDetails) {
        if (!gradle.daemonIdleTimeoutConfigurable && OperatingSystem.current().isWindows()) {
            // Older daemon don't have configurable ttl and they hung for 3 hours afterwards.
            // This is a real problem on windows due to eager file locking and continuous CI failures.
            // On linux it's a lesser problem - long-lived daemons hung and steal resources but don't lock files.
            // So, for windows we'll only run tests against target gradle that supports ttl
            return false
        }
        ToolingApiVersion toolingVersionAnnotation = testDetails.getAnnotation(ToolingApiVersion)
        Spec<GradleVersion> toolingVersionSpec = toVersionSpec(toolingVersionAnnotation)
        if (!toolingVersionSpec.isSatisfiedBy(this.toolingApiVersion)) {
            return false
        }
        TargetGradleVersion gradleVersionAnnotation = testDetails.getAnnotation(TargetGradleVersion)
        Spec<GradleVersion> gradleVersionSpec = toVersionSpec(gradleVersionAnnotation)
        if (!gradleVersionSpec.isSatisfiedBy(this.gradleVersion)) {
            return false
        }

        return true
    }

    private static Spec<GradleVersion> toVersionSpec(annotation) {
        if (annotation == null) {
            return Specs.SATISFIES_ALL
        }
        if (annotation.value() == "current") {
            return GradleVersionSpec.toSpec("=${INSTALLATION_GRADLE_VERSION.baseVersion.version}")
        }
        return GradleVersionSpec.toSpec(annotation.value())
    }

    @Override
    protected void before(IMethodInvocation invocation) {
        ((ToolingApiSpecification)invocation.getInstance()).setTargetDist(gradle)
    }
}
