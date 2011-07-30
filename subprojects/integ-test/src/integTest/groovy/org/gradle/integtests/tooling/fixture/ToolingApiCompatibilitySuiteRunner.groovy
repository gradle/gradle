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

import org.gradle.integtests.fixtures.GradleDistribution
import org.junit.runner.Description
import org.junit.runner.Request
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.Failure
import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.util.ObservableUrlClassLoader
import org.gradle.util.ClasspathUtil
import org.gradle.util.MultiParentClassLoader
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestFile
import org.gradle.util.DefaultClassLoaderFactory

/**
 * Executes instances of {@link ToolingApiCompatibilitySuite}.
 */
class ToolingApiCompatibilitySuiteRunner extends Runner {
    private final Class<ToolingApiCompatibilitySuite> target
    private final Description description
    private final List<Permutation> permutations = []
    private static final Map<String, ClassLoader> TEST_CLASS_LOADERS = [:]

    def dist = new GradleDistribution()
    def m3 = dist.previousVersion("1.0-milestone-3")
    def m4 = dist.previousVersion("1.0-milestone-4")

    ToolingApiCompatibilitySuiteRunner(Class<ToolingApiCompatibilitySuite> target) {
        this.target = target
        def suite = target.newInstance()
        this.description = Description.createSuiteDescription(target)
        permutations << new Permutation(suite, description, dist, m3)
        permutations << new Permutation(suite, description, m3, dist)
        permutations << new Permutation(suite, description, dist, m4)
        permutations << new Permutation(suite, description, m4, dist)
    }

    @Override
    Description getDescription() {
        return description
    }

    @Override
    void run(RunNotifier notifier) {
        permutations.each { it.run(notifier) }
    }

    private static class Permutation {
        final BasicGradleDistribution toolingApi
        final BasicGradleDistribution gradle
        final Runner runner
        final ToolingApiCompatibilitySuite suite
        final Map<Description, Description> descriptionTranslations = [:]

        Permutation(ToolingApiCompatibilitySuite suite, Description parent, BasicGradleDistribution toolingApi, BasicGradleDistribution gradle) {
            this.toolingApi = toolingApi
            this.gradle = gradle
            this.suite = suite
            if (suite.accept(toolingApi, gradle)) {
                runner = Request.classes(loadClasses() as Class[]).getRunner()
                map(runner.description, parent)
            } else {
                runner = null;
            }
        }

        private List<Class> loadClasses() {
            def testClassLoader = getTestClassLoader()
            return suite.classes.collect { testClassLoader.loadClass(it.name) }
        }

        private ClassLoader getTestClassLoader() {
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
            sharedClassLoader.allowClass(TargetDistSelector)
            sharedClassLoader.allowPackage('org.gradle.integtests.fixtures')

            def parentClassLoader = new MultiParentClassLoader(toolingApiClassLoader, sharedClassLoader)

            def testClassPath = []
            testClassPath << ClasspathUtil.getClasspathForClass(suite.class)

            return new ObservableUrlClassLoader(parentClassLoader, testClassPath.collect { it.toURI().toURL() })
        }

        private void map(Description source, Description target) {
            source.children.each { child ->
                def mappedChild
                if (child.methodName) {
                    mappedChild = Description.createSuiteDescription("${child.methodName} [${toolingApi} -> ${gradle}](${child.className})")
                } else {
                    mappedChild = Description.createSuiteDescription(child.className)
                }
                target.addChild(mappedChild)
                descriptionTranslations.put(child, mappedChild)
                map(child, mappedChild)
            }
        }

        void run(RunNotifier notifier) {
            if (runner == null) {
                def description = Description.createSuiteDescription("ignored [${toolingApi} -> ${gradle}](${suite.class.name})")
                notifier.fireTestIgnored(description)
                return
            }

            RunNotifier nested = new RunNotifier()
            nested.addListener(new RunListener() {
                @Override
                void testStarted(Description description) {
                    notifier.fireTestStarted(descriptionTranslations[description])
                }

                @Override
                void testFailure(Failure failure) {
                    notifier.fireTestFailure(new Failure(descriptionTranslations[failure.description], failure.exception))
                }

                @Override
                void testAssumptionFailure(Failure failure) {
                    notifier.fireTestAssumptionFailed(new Failure(descriptionTranslations[failure.description], failure.exception))
                }

                @Override
                void testIgnored(Description description) {
                    notifier.fireTestIgnored(descriptionTranslations[description])
                }

                @Override
                void testFinished(Description description) {
                    notifier.fireTestFinished(descriptionTranslations[description])
                }
            })

            TargetDistSelector.select(gradle.version)
            try {
                runner.run(nested)
            } finally {
                TargetDistSelector.unselect()
            }
        }
    }
}
