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
package org.gradle.integtests.tooling.fixture

import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.integtests.fixtures.AbstractCompatibilityTestRunner
import org.gradle.integtests.fixtures.AbstractMultiTestRunner
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.*

class ToolingApiCompatibilitySuiteRunner extends AbstractCompatibilityTestRunner {
    private static final Map<String, ClassLoader> TEST_CLASS_LOADERS = [:]

    ToolingApiCompatibilitySuiteRunner(Class<? extends ToolingApiSpecification> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        def resolver = new ToolingApiDistributionResolver().withDefaultRepository()
        try {
            add(new Permutation(resolver.resolve(current.version.version), current))
            previous.each {
                if (it.toolingApiSupported) {
                    add(new Permutation(resolver.resolve(current.version.version), it))
                    add(new Permutation(resolver.resolve(it.version.version), current))
                }
            }
        } finally {
            resolver.stop()
        }
    }

    private class Permutation extends AbstractMultiTestRunner.Execution {
        final ToolingApiDistribution toolingApi
        final GradleDistribution gradle

        Permutation(ToolingApiDistribution toolingApi, GradleDistribution gradle) {
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

        private String displayName(GradleVersion version) {
            if (version == GradleVersion.current()) {
                return "current"
            }
            return version.version
        }

        @Override
        protected boolean isTestEnabled(AbstractMultiTestRunner.TestDetails testDetails) {
            if (!gradle.daemonSupported) {
                return false
            }
            if (!gradle.daemonIdleTimeoutConfigurable && OperatingSystem.current().isWindows()) {
                //Older daemon don't have configurable ttl and they hung for 3 hours afterwards.
                // This is a real problem on windows due to eager file locking and continuous CI failures.
                // On linux it's a lesser problem - long-lived daemons hung and steal resources but don't lock files.
                // So, for windows we'll only run tests against target gradle that supports ttl
                return false
            }
            ToolingApiVersion minToolingApiVersion = testDetails.getAnnotation(ToolingApiVersion)
            if (!toVersionSpec(minToolingApiVersion).isSatisfiedBy(toolingApi.version)) {
                return false
            }
            MinTargetGradleVersion minTargetGradleVersion = testDetails.getAnnotation(MinTargetGradleVersion)
            if (minTargetGradleVersion && gradle.version < extractVersion(minTargetGradleVersion)) {
                return false
            }
            MaxTargetGradleVersion maxTargetGradleVersion = testDetails.getAnnotation(MaxTargetGradleVersion)
            if (maxTargetGradleVersion && gradle.version > extractVersion(maxTargetGradleVersion)) {
                return false
            }

            return true
        }

        private GradleVersion extractVersion(annotation) {
            if ("current".equals(annotation.value())) {
                //so that one can use 'current' literal in the annotation value
                //(useful if you don't know if the feature makes its way to the upcoming release)
                return GradleVersion.current()
            }
            return GradleVersion.version(annotation.value())
        }

        private Spec<GradleVersion> toVersionSpec(annotation) {
            if (annotation == null) {
                return Specs.SATISFIES_ALL
            }
            String value = annotation.value().trim()
            if (value.startsWith('>=')) {
                def minVersion = GradleVersion.version(value.substring(2))
                return { version -> version >= minVersion } as Spec
            } else if (value.startsWith('<=')) {
                def maxVersion = GradleVersion.version(value.substring(2))
                return { version -> version <= maxVersion } as Spec
            } else {
                throw new RuntimeException("Unsupported version range '$value' specified in @${annotation.class.simpleName}. Supported formats: '>=nnn' or '<=nnn'")
            }
        }

        @Override
        protected List<? extends Class<?>> loadTargetClasses() {
            def testClassLoader = getTestClassLoader()
            return [testClassLoader.loadClass(target.name)]
        }

        private ClassLoader getTestClassLoader() {
            synchronized(ToolingApiCompatibilitySuiteRunner) {
                def classLoader = TEST_CLASS_LOADERS.get(toolingApi.version)
                if (!classLoader) {
                    classLoader = createTestClassLoader()
                    TEST_CLASS_LOADERS.put(toolingApi.version, classLoader)
                }
                return classLoader
            }
        }

        private ClassLoader createTestClassLoader() {
            def classLoaderFactory = new DefaultClassLoaderFactory()

            def sharedClassLoader = classLoaderFactory.createFilteringClassLoader(getClass().classLoader)
            sharedClassLoader.allowPackage('org.junit')
            sharedClassLoader.allowPackage('org.hamcrest')
            sharedClassLoader.allowPackage('junit.framework')
            sharedClassLoader.allowPackage('groovy')
            sharedClassLoader.allowPackage('org.codehaus.groovy')
            sharedClassLoader.allowPackage('spock')
            sharedClassLoader.allowPackage('org.spockframework')
            sharedClassLoader.allowClass(SetSystemProperties)
            sharedClassLoader.allowPackage('org.gradle.integtests.fixtures')
            sharedClassLoader.allowPackage('org.gradle.test.fixtures')
            sharedClassLoader.allowClass(OperatingSystem)
            sharedClassLoader.allowClass(Requires)
            sharedClassLoader.allowClass(TestPrecondition)
            sharedClassLoader.allowClass(MaxTargetGradleVersion)
            sharedClassLoader.allowClass(MinTargetGradleVersion)
            sharedClassLoader.allowClass(ToolingApiVersion)
            sharedClassLoader.allowResources(target.name.replace('.', '/'))

            def parentClassLoader = new MultiParentClassLoader(toolingApi.classLoader, sharedClassLoader)

            def testClassPath = []
            testClassPath << ClasspathUtil.getClasspathForClass(target)

            return new MutableURLClassLoader(parentClassLoader, testClassPath.collect { it.toURI().toURL() })
        }

        @Override
        protected void before() {
            testClassLoader.loadClass(ToolingApiSpecification.name).selectTargetDist(gradle)
        }
    }
}
