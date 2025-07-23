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
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters

class FailedSyncIntegrationTest extends AbstractIntegrationSpec implements ToolingApiSpec {

    def setup() {
        executer.withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
    }

    def "basic build - broken main settings file"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """

        when:
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":"]
    }

    def "basic build - broken root build file"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildKotlinFile << """
            blow up !!!
        """

        when:
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":"]
    }

    def "basic project w/ included build - broken build file in included build"() {
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
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":", ":included"]
    }

    def "basic build w/ included build - broken settings file in included build"() {
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
            // nothing interesting
        """

        when:
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":", ":included"]
    }

    def "basic build w/ included build - broken settings and build file in included build"() {
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
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":", ":included"]
    }


    @Override
    GradleExecuter createExecuter() {
        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
    }

}
