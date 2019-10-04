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

import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.integtests.fixtures.AbstractMultiTestRunner
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.GradleVersion
import spock.lang.Unroll

class ToolingApiExecution extends AbstractMultiTestRunner.Execution implements ToolingApiClasspathProvider {
    private static final Map<String, ClassLoader> TEST_CLASS_LOADERS = [:]

    final ToolingApiDistribution toolingApi
    final GradleDistribution gradle

    ToolingApiExecution(ToolingApiDistribution toolingApi, GradleDistribution gradle) {
        this.toolingApi = toolingApi
        this.gradle = gradle
    }

    @Override
    protected String getDisplayName() {
        return "${displayName(toolingApi.version)} -> ${displayName(gradle.version)}"
    }

    @Override
    String toString() {
        return displayName
    }

    protected String displayName(GradleVersion version) {
        if (version == GradleVersion.current()) {
            return "current"
        }
        return version.version
    }

    @Override
    protected boolean isTestEnabled(AbstractMultiTestRunner.TestDetails testDetails) {
        // Trying to use @Unroll with a tooling api runner causes NPEs in the test fixtures
        // Fail early with a message until we can fix this properly.
        Unroll unroll = testDetails.getAnnotation(Unroll)
        if (unroll != null) {
            throw new IllegalArgumentException("Cannot use @Unroll with Tooling Api tests")
        }

        if (!gradle.daemonIdleTimeoutConfigurable && OperatingSystem.current().isWindows()) {
            // Older daemon don't have configurable ttl and they hung for 3 hours afterwards.
            // This is a real problem on windows due to eager file locking and continuous CI failures.
            // On linux it's a lesser problem - long-lived daemons hung and steal resources but don't lock files.
            // So, for windows we'll only run tests against target gradle that supports ttl
            return false
        }
        ToolingApiVersion toolingApiVersion = testDetails.getAnnotation(ToolingApiVersion)
        if (!toVersionSpec(toolingApiVersion).isSatisfiedBy(toolingApi.version)) {
            return false
        }
        TargetGradleVersion targetGradleVersion = testDetails.getAnnotation(TargetGradleVersion)
        if (!toVersionSpec(targetGradleVersion).isSatisfiedBy(gradle.version)) {
            return false
        }

        return true
    }

    private Spec<GradleVersion> toVersionSpec(annotation) {
        if (annotation == null) {
            return Specs.SATISFIES_ALL
        }
        return GradleVersionSpec.toSpec(annotation.value())
    }

    @Override
    protected List<? extends Class<?>> loadTargetClasses() {
        def testClassLoader = getTestClassLoader()
        return [testClassLoader.loadClass(target.name)]
    }

    ClassLoader getTestClassLoader() {
        def testClassPath = []
        testClassPath << ClasspathUtil.getClasspathForClass(target)
        testClassPath << ClasspathUtil.getClasspathForClass(TestResultHandler)

        testClassPath.addAll(collectAdditionalClasspath())

        getTestClassLoader(TEST_CLASS_LOADERS, toolingApi, testClassPath) {
            it.allowResources(target.name.replace('.', '/'))
        }
    }

    private List<File> collectAdditionalClasspath() {
        target.annotations.findAll { it instanceof ToolingApiAdditionalClasspath }.collectMany { annotation ->
            (annotation as ToolingApiAdditionalClasspath).value()
                .newInstance()
                .additionalClasspathFor(toolingApi, gradle)
        }
    }

    @Override
    protected void before() {
        def testClazz = testClassLoader.loadClass(ToolingApiSpecification.name)
        testClazz.selectTargetDist(gradle)
    }
}
