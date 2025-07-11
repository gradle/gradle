/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.tooling.fixture.ToolingApiBackedGradleExecuter
import org.gradle.integtests.tooling.fixture.ToolingApiSpec
import org.gradle.plugins.ide.internal.tooling.model.PartialBuildInfo
import org.gradle.plugins.ide.internal.tooling.model.ResilientConfigurationException
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters

class FailedSyncIntegrationTest extends AbstractIntegrationSpec implements ToolingApiSpec {

    def "broken settings file - strict mode- build action"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """

        when:
        MyCustomModel model = runBuildActionFails(new CustomModelAction())

        then:
        failureDescriptionContains("Script compilation error")
    }

    def "basic project - broken root build file with build action"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildKotlinFile << """
            blow up !!!
        """

        when:
        executer.withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":"]
    }

    def "basic project w/ included build - broken included build build file - build action"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = testDirectory.createDir("included")
        included.file("settings.gradle.kts") << """
            rootProject.name = "included"
        """
        included.file("build.gradle.kts") << """
            blow up !!!
        """

        when:
        executer.withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":", ":included"]
    }

    def "basic project w/ included build - broken included build settings file and build script - strict mode - build action"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = testDirectory.createDir("included")
        included.file("settings.gradle.kts") << """
            boom !!!
        """
        included.file("build.gradle.kts") << """
            blow up !!!
        """

        when:
        MyCustomModel model = runBuildActionFails(new CustomModelAction())

        then:
        failureDescriptionContains("Script compilation error")
    }

    def "basic project with settings included build - broken included build build file - build action"() {
        given:
        settingsKotlinFile << """
            pluginManagement {
                includeBuild("included")
            }
            rootProject.name = "root"
        """

        def included = testDirectory.createDir("included")
        included.file("settings.gradle.kts") << """
            boom !!!
            rootProject.name = "included"
        """
        included.file("build.gradle.kts") << """
            plugins {
                `java-library`
            }
        """

        when:
        runBuildAction(new CustomModelAction())

        then:
        def e = thrown(BuildActionFailureException)
        // It seems these are loaded from different classloaders, so we cannot use `instanceof`
        e.cause.class.name == ResilientConfigurationException.class.name
        List<PartialBuildInfo> partialBuildInfos = e.cause.partialBuildInfos
        partialBuildInfos.collect { it.buildId } == [":", ":included"]
        partialBuildInfos.collect { it.projectDir } == [
            testDirectory,
            testDirectory.file("included")
        ]
        partialBuildInfos.collect { it.scriptFile } == [
            testDirectory.file("settings.gradle.kts"),
            testDirectory.file("included").file("settings.gradle.kts")
        ]
    }

    @Override
    GradleExecuter createExecuter() {
        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
    }

}
