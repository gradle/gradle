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
import org.gradle.integtests.fixtures.IgnoreVersions
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classpath.DefaultClassPath
import org.junit.Rule
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.InvocationTargetException

@IgnoreVersions({!it.openApiSupported})
class CrossVersionCompatibilityIntegrationTest extends CrossVersionIntegrationSpec {
    private final Logger logger = LoggerFactory.getLogger(CrossVersionCompatibilityIntegrationTest)
    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    public void cannotUseOpenApiFromOlderVersionToBuildUsingCurrentVersion() {
        def classloader = loadOpenApi(previous)

        when:
        attemptToCreateSinglePaneUI(classloader)

        then:
        InvocationTargetException e = thrown()
        e.cause.message == 'Support for the Gradle Open API was removed in the Gradle 2.0 release. You should use the Gradle tooling API instead.'

        when:
        attemptToCreateDualPaneUI(classloader)

        then:
        e = thrown()
        e.cause.message == 'Support for the Gradle Open API was removed in the Gradle 2.0 release. You should use the Gradle tooling API instead.'

        when:
        attemptToCreateRunner(classloader)

        then:
        e = thrown()
        e.cause.message == 'Support for the Gradle Open API was removed in the Gradle 2.0 release. You should use the Gradle tooling API instead.'
    }

    void attemptToCreateSinglePaneUI(ClassLoader classloader) {
        def interactionType = classloader.loadClass("org.gradle.openapi.external.ui.SinglePaneUIInteractionVersion1")
        def interaction = [:].asType(interactionType)
        def factory = classloader.loadClass("org.gradle.openapi.external.ui.UIFactory")
        def method = factory.getMethods().find { it.name == 'createSinglePaneUI' }
        try {
            method.invoke(null, classloader, current.gradleHomeDir, interaction, true)
        } catch (InvocationTargetException e) {
            throw e.cause
        }
    }

    void attemptToCreateDualPaneUI(ClassLoader classloader) {
        def interactionType = classloader.loadClass("org.gradle.openapi.external.ui.DualPaneUIInteractionVersion1")
        def interaction = [:].asType(interactionType)
        def factory = classloader.loadClass("org.gradle.openapi.external.ui.UIFactory")
        def method = factory.getMethods().find { it.name == 'createDualPaneUI' }
        try {
            method.invoke(null, classloader, current.gradleHomeDir, interaction, true)
        } catch (InvocationTargetException e) {
            throw e.cause
        }
    }

    void attemptToCreateRunner(ClassLoader classloader) {
        def interactionType = classloader.loadClass("org.gradle.openapi.external.runner.GradleRunnerInteractionVersion1")
        def interaction = [:].asType(interactionType)
        def factory = classloader.loadClass("org.gradle.openapi.external.runner.GradleRunnerFactory")
        def method = factory.getMethods().find { it.name == 'createGradleRunner' }
        try {
            method.invoke(null, classloader, current.gradleHomeDir, interaction, true)
        } catch (InvocationTargetException e) {
            throw e.cause
        }
    }

    private ClassLoader loadOpenApi(GradleDistribution openApiVersion) {
        def classpath = openApiVersion.gradleHomeDir.file('lib').listFiles().findAll { it.name =~ /gradle-open-api.*\.jar/ }
        logger.info('Using Open API classpath {}', classpath)
        new DefaultClassLoaderFactory().createIsolatedClassLoader(new DefaultClassPath(classpath))
    }
}
