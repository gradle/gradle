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

package org.gradle.internal.cc.impl.sync

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.cc.impl.fixtures.ToolingApiBackedGradleExecuter
import org.gradle.internal.cc.impl.fixtures.ToolingApiSpec
import org.gradle.tooling.model.gradle.GradleBuild

// TODO: wrong package/project, just using for convenience

class FailedSyncIntegrationTest extends AbstractIntegrationSpec implements ToolingApiSpec {

    def "broken settings file - strict mode"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """

        when:
        fetchModelFails(GradleBuild)

        then:
        failureDescriptionContains("Script compilation error")
    }

    def "broken settings file - lenient mode"() {
        given:
        System.setProperty("org.gradle.kotlin.dsl.provider.mode", "classpath")
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b")
            // comment
            boom!!!
        """
        testDirectory.createDir("a").file("build.gradle.kts") << ""
        testDirectory.createDir("b").file("build.gradle.kts") << ""


        when:
        GradleBuild model = fetchModel(GradleBuild)

        then:
        model.rootProject.name != "root"
        model.projects.size() == 1
    }

    def "basic project - broken root build file"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildKotlinFile << """
            blow up !!!
        """

        when:
        GradleBuild model = fetchModel(GradleBuild)

        then:
        model.rootProject.name == "root"
        model.projects.size() == 1
        model.projects[0].name == "root"
    }

    def "basic project w/ included build - broken included build settings"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """
        testDirectory.createDir("included").file("settings.gradle.kts") << """
            blow up !!!
        """

        when:
        GradleBuild model = fetchModel(GradleBuild)

        then:
        model.rootProject.name == "root"
        model.projects.size() == 1
        model.projects[0].name == "root"
        model.includedBuilds.size() == 1
        model.includedBuilds[0].rootProject.name == "included"
    }

    def "basic project w/ included build - broken included build build file"() {
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
        GradleBuild model = fetchModel(GradleBuild)

        then:
        model.rootProject.name == "root"
        model.projects.size() == 1
        model.projects[0].name == "root"
        model.includedBuilds.size() == 1
        model.includedBuilds[0].rootProject.name == "included"
    }

    def "basic project w/ included plugin build - broken included build build file"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            pluginManagement {
                includeBuild("included")
            }
        """

        def included = testDirectory.createDir("included")
        included.file("settings.gradle.kts") << """
            rootProject.name = "included"
        """
        included.file("build.gradle.kts") << """
            blow up !!!
        """

        when:
        GradleBuild model = fetchModel(GradleBuild)

        then:
        model.rootProject.name == "root"
        model.projects.size() == 1
        model.projects[0].name == "root"
        model.includedBuilds.size() == 1
        model.includedBuilds[0].rootProject.name == "included"
    }

    @Override
    GradleExecuter createExecuter() {
        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
    }

}
