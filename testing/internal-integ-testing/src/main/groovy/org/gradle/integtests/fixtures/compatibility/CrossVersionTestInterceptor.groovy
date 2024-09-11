/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures.compatibility

import org.gradle.integtests.fixtures.GradleDistributionTool
import org.gradle.integtests.fixtures.IgnoreVersions
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.util.GradleVersion
import org.spockframework.runtime.extension.IMethodInvocation

/**
 * <p>Executes instances of {@link org.gradle.integtests.fixtures.CrossVersionIntegrationSpec} against each previous Gradle version.
 *
 * <p>Sets the {@link org.gradle.integtests.fixtures.CrossVersionIntegrationSpec#previous} property of the test instance before executing it.
 *
 * <p>A test class can be annotated with {@link org.gradle.integtests.fixtures.TargetVersions} to specify the set of versions the test is compatible with, and {@link org.gradle.integtests.fixtures.IgnoreVersions} to specify the set of versions the
 * test should not be run for.
 * <p>
 * See {@link AbstractContextualMultiVersionTestInterceptor} for information on running these tests.
 */
class CrossVersionTestInterceptor extends AbstractCompatibilityTestInterceptor {
    CrossVersionTestInterceptor(Class<?> target) {
        super(target)
    }

    /**
     * Cross version tests will run against any _supported_ Gradle version: currently >= 1.0
     */
    protected List<GradleDistribution> choosePreviousVersionsToTest(ReleasedVersionDistributions previousVersions) {
        return previousVersions.getSupported()
    }

    @Override
    protected Collection<Execution> createDistributionExecutionsFor(GradleDistributionTool versionedTool) {
        GradleDistribution distribution = versionedTool.getDistribution()
        return [new PreviousVersionExecution(distribution, isEnabled(distribution))]
    }

    protected boolean isEnabled(GradleDistribution previousVersion) {
        Closure ignoreVersions = getAnnotationClosure(target, IgnoreVersions, {})
        if (ignoreVersions(previousVersion)) {
            return false
        }

        def versionsAnnotation = target.getAnnotation(TargetVersions)
        if (versionsAnnotation == null) {
            return true
        }

        List<String> targetGradleVersions = versionsAnnotation.value()
        for (String targetGradleVersion : targetGradleVersions) {
            if (isMatching(targetGradleVersion, previousVersion.version.version)) {
                return true
            }
        }
        return false
    }

    private static boolean isMatching(String targetGradleVersion, String candidate) {
        if (targetGradleVersion.endsWith('+')) {
            def minVersion = targetGradleVersion.substring(0, targetGradleVersion.length() - 1)
            return GradleVersion.version(minVersion) <= GradleVersion.version(candidate)
        }
        return targetGradleVersion == candidate
    }

    private static Closure getAnnotationClosure(Class target, Class annotation, Closure defaultValue) {
        def a = target.getAnnotation(annotation)
        a ? a.value().newInstance(target, target) : defaultValue
    }

    private static class PreviousVersionExecution extends org.gradle.integtests.fixtures.extensions.AbstractMultiTestInterceptor.Execution {
        final GradleDistribution previousVersion
        final boolean enabled

        PreviousVersionExecution(GradleDistribution previousVersion, boolean enabled) {
            this.previousVersion = previousVersion
            this.enabled = enabled
        }

        @Override
        String getDisplayName() {
            return previousVersion.version.version
        }

        @Override
        String toString() {
            return getDisplayName()
        }

        @Override
        protected void before(IMethodInvocation invocation) {
            target.previous = previousVersion
        }

        @Override
        boolean isTestEnabled(org.gradle.integtests.fixtures.extensions.AbstractMultiTestInterceptor.TestDetails testDetails) {
            return enabled
        }
    }
}
