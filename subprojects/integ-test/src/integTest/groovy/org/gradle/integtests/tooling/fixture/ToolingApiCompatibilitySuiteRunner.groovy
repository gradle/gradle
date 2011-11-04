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
import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.util.*
import static org.gradle.util.GradleVersion.version

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
        super(target)
    }

    @Override
    protected List<Permutation> createExecutions() {
        List<Permutation> permutations = []
        permutations << new Permutation(current, current)
        previous.each {
            if (it.toolingApiSupported) {
                permutations << new Permutation(current, it)
                permutations << new Permutation(it, current)
            }
        }
        return permutations
    }

    private class Permutation extends AbstractCompatibilityTestRunner.Execution {
        final BasicGradleDistribution toolingApi
        final BasicGradleDistribution gradle

        Permutation(BasicGradleDistribution toolingApi, BasicGradleDistribution gradle) {
            this.toolingApi = toolingApi
            this.gradle = gradle
        }

        @Override
        protected String getDisplayName() {
            return "${displayName(toolingApi)} -> ${displayName(gradle)}"
        }

        private String displayName(BasicGradleDistribution dist) {
            if (dist.version == GradleVersion.current().version) {
                return "current"
            }
            return dist.version
        }

        @Override
        protected boolean isEnabled() {
            MinToolingApiVersion minToolingApiVersion = target.getAnnotation(MinToolingApiVersion)
            if (minToolingApiVersion && GradleVersion.version(toolingApi.version) < GradleVersion.version(minToolingApiVersion.value())) {
                return false
            }
            MinTargetGradleVersion minTargetGradleVersion = target.getAnnotation(MinTargetGradleVersion)
            if (minTargetGradleVersion && GradleVersion.version(gradle.version) < GradleVersion.version(minTargetGradleVersion.value())) {
                return false
            }
            return true
        }

        @Override
        protected List<? extends Class<?>> loadTargetClasses() {
            def testClassLoader = getTestClassLoader()
            return [testClassLoader.loadClass(target.name)]
        }

        private ClassLoader getTestClassLoader() {
            if (toolingApi.version == GradleVersion.current().version) {
                return getClass().classLoader
            }
            def classLoader = TEST_CLASS_LOADERS.get(toolingApi.version)
            if (!classLoader) {
                classLoader = createTestClassLoader()
                TEST_CLASS_LOADERS.put(toolingApi.version, classLoader)
            }
            return classLoader
        }

        private def createTestClassLoader() {
            def toolingApiClassPath = []
            toolingApiClassPath += toolingApi.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /gradle-tooling-api.*\.jar/ }
            toolingApiClassPath += toolingApi.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /gradle-core.*\.jar/ }
            toolingApiClassPath += toolingApi.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /slf4j-api.*\.jar/ }

            // Add in an slf4j provider
            toolingApiClassPath += toolingApi.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /logback.*\.jar/ }

            def classLoaderFactory = new DefaultClassLoaderFactory()
            def toolingApiClassLoader = classLoaderFactory.createIsolatedClassLoader(toolingApiClassPath.collect { it.toURI().toURL() })

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

            def parentClassLoader = new MultiParentClassLoader(toolingApiClassLoader, sharedClassLoader)

            def testClassPath = []
            testClassPath << ClasspathUtil.getClasspathForClass(target)

            return new ObservableUrlClassLoader(parentClassLoader, testClassPath.collect { it.toURI().toURL() })
        }

        @Override
        protected void before() {
            testClassLoader.loadClass(ToolingApiSpecification.name).select(gradle)
        }
    }
}
