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
package org.gradle.integtests.tooling.m3

import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.integtests.fixtures.GradleDistribution
import org.junit.Ignore
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.util.*

/**
 * @author: Szczepan Faber, created at: 6/29/11
 */
class ToolingApiCompatibilitySuite {

    private final Logger logger = LoggerFactory.getLogger(ToolingApiCompatibilitySuite)

    def dist = new GradleDistribution()
    def m3 = dist.previousVersion("1.0-milestone-3")

    @Test
    public void "m3 ToolingApi against current Gradle" () {
        tests(org.gradle.integtests.tooling.m3.ToolingApiSuite, m3, dist).shouldPass()
    }

    @Test
    public void "current ToolingApi against m3 Gradle" () {
        tests(org.gradle.integtests.tooling.m3.ToolingApiSuite, dist, m3).shouldPass()
    }

    @Ignore
    @Test
    public void "new features of current ToolingApi against m3 Gradle should fail" () {
        //sanity test that checks if the test harness works fine
        //I don't we need such test per every milestone
        tests(org.gradle.integtests.tooling.m4.ToolingApiSuite, dist, m3).shouldFail()
    }

    def tests(Class<?> testClass, BasicGradleDistribution toolingApi, BasicGradleDistribution gradle) {
        def toolingApiClassPath = []
        toolingApiClassPath += toolingApi.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /gradle-tooling-api.*\.jar/ }
        toolingApiClassPath += toolingApi.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /gradle-core.*\.jar/ }
        toolingApiClassPath += toolingApi.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /slf4j-api.*\.jar/ }

        String classpathEntries = "Tooling API classpath used: "
        toolingApiClassPath.each {
            classpathEntries += " -> $it\n"
        }
        logger.debug(classpathEntries)

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
        testClassPath << ClasspathUtil.getClasspathForClass(testClass)

        def classloader = new ObservableUrlClassLoader(parentClassLoader, testClassPath.collect { it.toURI().toURL() })
        def allTests = classloader.loadClass(testClass.name).newInstance()
        return allTests.run(gradle.version)
    }
}