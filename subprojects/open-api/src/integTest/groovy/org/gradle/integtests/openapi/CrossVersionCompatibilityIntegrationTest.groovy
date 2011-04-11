/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests.openapi

import org.gradle.api.internal.AbstractClassPathProvider
import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.TestResources
import org.gradle.util.Jvm
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CrossVersionCompatibilityIntegrationTest {
    private final Logger logger = LoggerFactory.getLogger(CrossVersionCompatibilityIntegrationTest)
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final TestResources resources = new TestResources()

    private final BasicGradleDistribution gradle09rc3 = dist.previousVersion('0.9-rc-3')
    private final BasicGradleDistribution gradle09 = dist.previousVersion('0.9')
    private final BasicGradleDistribution gradle091 = dist.previousVersion('0.9.1')
    private final BasicGradleDistribution gradle092 = dist.previousVersion('0.9.2')
    private final BasicGradleDistribution gradle10Milestone1 = dist.previousVersion('1.0-milestone-1')
    private final BasicGradleDistribution gradle10Milestone2 = dist.previousVersion('1.0-milestone-2')

    @Test
    public void canUseOpenApiFromCurrentVersionToBuildUsingAnOlderVersion() {
        [gradle09rc3, gradle09, gradle091, gradle092, gradle10Milestone1, gradle10Milestone2].each {
            checkCanBuildUsing(dist, it)
        }
    }

    @Test
    public void canUseOpenApiFromOlderVersionToBuildUsingCurrentVersion() {
        [gradle09rc3, gradle09, gradle091, gradle092, gradle10Milestone1, gradle10Milestone2].each {
            checkCanBuildUsing(it, dist)
        }
    }

    def checkCanBuildUsing(BasicGradleDistribution openApiVersion, BasicGradleDistribution buildVersion) {
        try {
            if (!buildVersion.worksWith(Jvm.current())) {
                System.out.println("skipping $buildVersion as it does not work with ${Jvm.current()}.")
                return
            }
            if (!openApiVersion.worksWith(Jvm.current())) {
                System.out.println("skipping $openApiVersion as it does not work with ${Jvm.current()}.")
                return
            }
            def testClasses = AbstractClassPathProvider.getClasspathForClass(CrossVersionBuilder.class)
            def junitJar = AbstractClassPathProvider.getClasspathForClass(Assert.class)
            def classpath = [testClasses, junitJar] + openApiVersion.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /gradle-open-api.*\.jar/ }
            logger.info('Using Open API classpath {}', classpath)
            def classloader = new URLClassLoader(classpath.collect { it.toURI().toURL() } as URL[], ClassLoader.systemClassLoader.parent)
            def builder = classloader.loadClass(CrossVersionBuilder.class.name).newInstance()
            builder.targetGradleHomeDir = buildVersion.gradleHomeDir
            builder.currentDir = dist.testDir
            builder.version = buildVersion.version
            builder.build()
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to build using $buildVersion via the open API of $openApiVersion", throwable)
        }
    }
}
