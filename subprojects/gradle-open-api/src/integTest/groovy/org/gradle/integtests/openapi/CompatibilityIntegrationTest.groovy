/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.integtests.DistributionIntegrationTestRunner
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.PreviousGradleVersionExecuter
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.internal.AbstractClassPathProvider
import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.junit.Assert
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.gradle.util.Jvm

@RunWith(DistributionIntegrationTestRunner.class)
class CompatibilityIntegrationTest {
    private final Logger logger = LoggerFactory.getLogger(CompatibilityIntegrationTest)
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources resources = new TestResources()

    private final PreviousGradleVersionExecuter gradle09rc1 = dist.previousVersion('0.9-rc-1')
    private final PreviousGradleVersionExecuter gradle09rc2 = dist.previousVersion('0.9-rc-2')

    @Test
    public void canUseOpenApiFromCurrentVersionToBuildUsingAnOlderVersion() {
        [gradle09rc1, gradle09rc2].each {
            checkCanBuildUsing(dist, it)
        }
    }

    @Test
    public void canUseOpenApiFromOlderVersionToBuildUsingCurrentVersion() {
        [gradle09rc1, gradle09rc2].each {
            checkCanBuildUsing(it, dist)
        }
    }

    def checkCanBuildUsing(BasicGradleDistribution openApiVersion, BasicGradleDistribution buildVersion) {
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
    }
}
