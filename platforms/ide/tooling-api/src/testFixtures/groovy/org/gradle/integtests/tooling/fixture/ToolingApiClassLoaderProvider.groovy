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

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.internal.jvm.JavaVersionParser
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.SupportedJavaVersionsExpectations
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.Requires
import org.gradle.test.precondition.TestPrecondition
import org.gradle.util.DebugUtil
import org.gradle.util.SetSystemProperties
import org.gradle.util.internal.RedirectStdOutAndErr

import java.nio.file.Paths

class ToolingApiClassLoaderProvider {
    private static final Map<String, ClassLoader> TEST_CLASS_LOADERS = [:]
    private static final GradleDistribution CURRENT_GRADLE = new UnderDevelopmentGradleDistribution(IntegrationTestBuildContext.INSTANCE)

    static ClassLoader getToolingApiClassLoader(ToolingApiDistribution toolingApi, Class<?> target) {
        def testClassPath = [ToolingApiSpecification, target]
            .collect { ClasspathUtil.getClasspathForClass(it) }
            .collect {it.toURI().toURL() }

        testClassPath.addAll(addTestClasspathFromSystemProperty("org.gradle.integtest.crossVersion.testClasspath"))
        testClassPath.addAll(collectAdditionalClasspath(toolingApi, target))

        getTestClassLoader(toolingApi, testClassPath)
    }

    private static List<URL> addTestClasspathFromSystemProperty(String systemProperty) {
        String testClasspathProperty = System.getProperty(systemProperty)
        if (testClasspathProperty == null || testClasspathProperty.isBlank()) {
            throw new IllegalStateException("$systemProperty system property not set")
        }

        def parts = testClasspathProperty.split(":")
        def testClassPath = new ArrayList(parts.size())

        parts.eachWithIndex { String entry, int i ->
            try {
                testClassPath.add(Paths.get(parts[i]).toAbsolutePath().toUri().toURL())
            } catch (Exception e) {
                throw new RuntimeException("Invalid classpath entry in $systemProperty[" + i + "]: " + parts[i], e)
            }
        }

        testClassPath
    }

    private static List<URL> collectAdditionalClasspath(ToolingApiDistribution toolingApi, Class<?> target) {
        // TODO: with https://github.com/gradle/gradle/issues/37033 evaluate if this functionality can be removed
        target.annotations.findAll { it instanceof ToolingApiAdditionalClasspath }.collectMany { annotation ->
            (annotation as ToolingApiAdditionalClasspath).value()
                .getDeclaredConstructor()
                .newInstance()
                .additionalClasspathFor(toolingApi, CURRENT_GRADLE)
        }
        .collect { it.toURI().toURL() }
    }

    private static ClassLoader getTestClassLoader(ToolingApiDistribution toolingApi, List<URL> testClasspath) {
        synchronized (ToolingApiClassLoaderProvider) {
            return TEST_CLASS_LOADERS.computeIfAbsent(toolingApi.version.version, v -> createTestClassLoader(toolingApi, testClasspath))
        }
    }

    private static ClassLoader createTestClassLoader(ToolingApiDistribution toolingApi, List<URL> testClassPath) {
        def classLoaderFactory = new DefaultClassLoaderFactory()

        def sharedSpec = new FilteringClassLoader.Spec()
        sharedSpec.allowPackage('org.junit')
        sharedSpec.allowPackage('org.hamcrest')
        sharedSpec.allowPackage('junit.framework')
        sharedSpec.allowPackage('groovy')
        sharedSpec.allowPackage('org.codehaus.groovy')
        sharedSpec.allowPackage('spock')
        sharedSpec.allowPackage('org.spockframework')
        sharedSpec.allowClass(SetSystemProperties)
        sharedSpec.allowClass(RedirectStdOutAndErr)
        sharedSpec.allowPackage('org.gradle.integtests.fixtures')
        sharedSpec.allowPackage('org.gradle.play.integtest.fixtures')
        sharedSpec.allowPackage('org.gradle.plugins.ide.fixtures')
        sharedSpec.allowPackage('org.gradle.test.fixtures')
        sharedSpec.allowPackage('org.gradle.test.preconditions')
        sharedSpec.allowPackage('org.gradle.nativeplatform.fixtures')
        sharedSpec.allowPackage('org.gradle.language.fixtures')
        sharedSpec.allowPackage('org.gradle.workers.fixtures')
        sharedSpec.allowPackage('org.gradle.launcher.daemon.testing')
        sharedSpec.allowPackage('org.gradle.tooling')
        sharedSpec.allowPackage('org.gradle.kotlin.dsl.tooling.fixtures')
        sharedSpec.allowPackage('org.gradle.api.problems.fixtures')
        sharedSpec.allowClass(OperatingSystem)
        sharedSpec.allowClass(Requires)
        sharedSpec.allowClass(TestPrecondition)
        sharedSpec.allowClass(TargetGradleVersion)
        sharedSpec.allowClass(ToolingApiVersion)
        sharedSpec.allowClass(TeeOutputStream)
        sharedSpec.allowClass(ClassLoaderFixture)
        sharedSpec.allowClass(SupportedJavaVersionsExpectations)
        sharedSpec.allowClass(JavaVersionParser)
        sharedSpec.allowClass(DebugUtil)
        sharedSpec.allowClass(Jvm)
        def sharedClassLoader = classLoaderFactory.createFilteringClassLoader(Thread.currentThread().getContextClassLoader(), sharedSpec)

        def parentClassLoader = new MultiParentClassLoader(toolingApi.classLoader, sharedClassLoader)

        return new VisitableURLClassLoader("tapi-test", parentClassLoader, testClassPath)
    }
}
