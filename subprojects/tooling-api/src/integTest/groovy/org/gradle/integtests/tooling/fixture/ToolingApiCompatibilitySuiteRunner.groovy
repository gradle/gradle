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

import org.gradle.integtests.fixtures.AbstractCompatibilityTestRunner
import org.gradle.integtests.fixtures.AbstractMultiTestRunner
import org.gradle.integtests.fixtures.executer.BasicGradleDistribution
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.*

/**
 * Executes instances of {@link ToolingApiSpecification} against all compatible versions of tooling API consumer
 * and provider, including the current Gradle version under test.
 *
 * <p>A test can be annotated with {@link MinToolingApiVersion} and {@link MinTargetGradleVersion} to indicate the
 * minimum tooling API or Gradle versions required for the test.
 */
class ToolingApiCompatibilitySuiteRunner extends AbstractCompatibilityTestRunner {
    private static final Map<String, ClassLoader> TEST_CLASS_LOADERS = [:]

    ToolingApiCompatibilitySuiteRunner(Class<? extends ToolingApiSpecification> target) {
        super(target, includesAllPermutations(target))
    }

    static String includesAllPermutations(Class target) {
        if (target.getAnnotation(IncludeAllPermutations)) {
            return "all";
        } else {
            return null; //just use whatever is the default
        }
    }

    @Override
    protected void createExecutions() {
        ToolingApiDistributionResolver resolver = new ToolingApiDistributionResolver().withDefaultRepository()

        add(new Permutation(resolver.resolve(current.version), current))
        previous.each {
            if (it.toolingApiSupported) {
                add(new Permutation(resolver.resolve(current.version), it))
                add(new Permutation(resolver.resolve(it.version), current))
            }
        }
    }

    private class Permutation extends AbstractMultiTestRunner.Execution {
        final ToolingApiDistribution toolingApi
        final BasicGradleDistribution gradle

        Permutation(ToolingApiDistribution toolingApi, BasicGradleDistribution gradle) {
            this.toolingApi = toolingApi
            this.gradle = gradle
        }

        @Override
        protected String getDisplayName() {
            return "${displayName(toolingApi)} -> ${displayName(gradle)}"
        }

        private String displayName(dist) {
            if (dist.version == GradleVersion.current().version) {
                return "current"
            }
            return dist.version
        }

        @Override
        protected boolean isEnabled() {
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
            MinToolingApiVersion minToolingApiVersion = target.getAnnotation(MinToolingApiVersion)
            if (minToolingApiVersion && GradleVersion.version(toolingApi.version) < extractVersion(minToolingApiVersion)) {
                return false
            }
            MinTargetGradleVersion minTargetGradleVersion = target.getAnnotation(MinTargetGradleVersion)
            if (minTargetGradleVersion && GradleVersion.version(gradle.version) < extractVersion(minTargetGradleVersion)) {
                return false
            }
            MaxTargetGradleVersion maxTargetGradleVersion = target.getAnnotation(MaxTargetGradleVersion)
            if (maxTargetGradleVersion && GradleVersion.version(gradle.version) > extractVersion(maxTargetGradleVersion)) {
                return false
            }

            return true
        }

        private GradleVersion extractVersion(annotation) {
            if (GradleVersion.current().isSnapshot() && GradleVersion.current().version.startsWith(annotation.value())) {
                //so that one can use an unreleased version in the annotation value
                return GradleVersion.current()
            }
            if ("current".equals(annotation.value())) {
                //so that one can use 'current' literal in the annotation value
                //(useful if you don't know if the feature makes its way to the upcoming release)
                return GradleVersion.current()
            }
            return GradleVersion.version(annotation.value())
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
            sharedClassLoader.allowClass(TestFile)
            sharedClassLoader.allowClass(SetSystemProperties)
            sharedClassLoader.allowPackage('org.gradle.integtests.fixtures')
            sharedClassLoader.allowPackage('org.gradle.test.fixtures')
            sharedClassLoader.allowClass(OperatingSystem)
            sharedClassLoader.allowClass(Requires)
            sharedClassLoader.allowClass(TestPrecondition)
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
