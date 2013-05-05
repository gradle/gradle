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

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.util.ClasspathUtil
import org.gradle.util.DefaultClassLoaderFactory
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Assert
import org.junit.Rule
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Requires(TestPrecondition.SWING)
class CrossVersionCompatibilityIntegrationTest extends CrossVersionIntegrationSpec {
    private final Logger logger = LoggerFactory.getLogger(CrossVersionCompatibilityIntegrationTest)
    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    public void canUseOpenApiFromCurrentVersionToBuildUsingAnOlderVersion() {
        expect:
        checkCanBuildUsing(current, previous)
    }

    public void canUseOpenApiFromOlderVersionToBuildUsingCurrentVersion() {
        expect:
        checkCanBuildUsing(previous, current)
    }

    void checkCanBuildUsing(GradleDistribution openApiVersion, GradleDistribution buildVersion) {
        if (!buildVersion.openApiSupported) {
            System.out.println("skipping $buildVersion as it does not support the open API.")
            return
        }
        if (!openApiVersion.openApiSupported) {
            System.out.println("skipping $openApiVersion as it does not support the open API.")
            return
        }
        def testClasses = ClasspathUtil.getClasspathForClass(CrossVersionBuilder.class)
        def junitJar = ClasspathUtil.getClasspathForClass(Assert.class)
        def classpath = [testClasses, junitJar] + openApiVersion.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /gradle-open-api.*\.jar/ }
        logger.info('Using Open API classpath {}', classpath)
        def classloader = new DefaultClassLoaderFactory().createIsolatedClassLoader(classpath.collect { it.toURI() })
        def builder = classloader.loadClass(CrossVersionBuilder.class.name).newInstance()
        builder.targetGradleHomeDir = buildVersion.gradleHomeDir
        builder.currentDir = testDirectory
        builder.version = buildVersion.version.version
        builder.build()
    }
}
