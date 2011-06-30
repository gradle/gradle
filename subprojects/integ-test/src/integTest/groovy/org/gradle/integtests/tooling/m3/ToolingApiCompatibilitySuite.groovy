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

import ch.qos.logback.core.joran.spi.JoranException
import junit.framework.Assert
import org.apache.commons.io.FileUtils
import org.apache.tools.ant.Task
import org.gradle.integtests.ClassLoaderIsolationHelper
import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.util.ClasspathUtil
import org.gradle.util.DefaultClassLoaderFactory
import org.gradle.util.SetSystemProperties
import org.junit.Ignore
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.impl.StaticLoggerBinder
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 6/29/11
 */
class ToolingApiCompatibilitySuite {

    private final Logger logger = LoggerFactory.getLogger(ToolingApiCompatibilitySuite)

    def dist = new GradleDistribution()
    def m3 = dist.previousVersion("1.0-milestone-3")

    @Test
    public void "m3 ToolingApi against current Gradle" () {
        tests("org.gradle.integtests.tooling.m3.ToolingApiSuite", m3, dist).shouldPass()
    }

    @Test
    public void "current ToolingApi against m3 Gradle" () {
        tests("org.gradle.integtests.tooling.m3.ToolingApiSuite", dist, m3).shouldPass()
    }

    @Ignore
    @Test
    public void "new features of current ToolingApi against m3 Gradle should fail" () {
        //sanity test that checks if the test harness works fine
        //I don't we need such test per every milestone
        tests("org.gradle.integtests.tooling.m4.ToolingApiSuite", dist, m3).shouldFail()
    }

    def tests(String testClass, BasicGradleDistribution toolingApi, BasicGradleDistribution gradle) {
        def classpath = []

        classpath << ClasspathUtil.getClasspathForClass(ClassLoaderIsolationHelper.class)
        classpath << ClasspathUtil.getClasspathForClass(Assert.class)
        classpath << ClasspathUtil.getClasspathForClass(GroovyObject.class)
        classpath << ClasspathUtil.getClasspathForClass(Specification.class)
        classpath << ClasspathUtil.getClasspathForClass(FileUtils.class)
        classpath << ClasspathUtil.getClasspathForClass(StaticLoggerBinder.class)
        classpath << ClasspathUtil.getClasspathForClass(JoranException.class)
        classpath << ClasspathUtil.getClasspathForClass(Task.class)
        classpath << ClasspathUtil.getClasspathForClass(LoggerFactory.class)
        classpath << ClasspathUtil.getClasspathForClass(SetSystemProperties.class)
        classpath << ClasspathUtil.getClasspathForClass(GradleDistribution.class)

        classpath += toolingApi.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /gradle-tooling-api.*\.jar/ }
        classpath += gradle.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /gradle-core.*\.jar/ }

        String classpathEntries = "Classpath used: "
        classpath.each {
            classpathEntries += " -> $it\n"
        }
        logger.debug(classpathEntries)

        def classloader = new DefaultClassLoaderFactory().createIsolatedClassLoader(classpath.collect { it.toURI().toURL() })

        def allTests = classloader.loadClass(testClass).newInstance()
        return allTests.run(gradle.version)
    }
}