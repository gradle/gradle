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

package org.gradle.integtests.tooling.r82

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import java.util.stream.Collectors
import java.util.stream.Stream

@TargetGradleVersion('>=8.2')
class FailedSyncCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`
    }

    def "broken settings file - strict mode - build action"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """

        when:
        fails {
            action(new CustomModelAction()).run()
        }

        then:
        def e = thrown(BuildActionFailureException)
        e.cause.message.contains(settingsKotlinFile.absolutePath)

        failure.assertHasDescription("Script compilation error")
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
        MyCustomModel model = succeeds {
            action(new CustomModelAction())
                .withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
                .run()
        }

        then:
        model.paths == [":"]
    }

    def "basic project w/ included build - broken included build build file - build action"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = file("included")
        included.file("settings.gradle.kts") << """
            rootProject.name = "included"
        """
        included.file("build.gradle.kts") << """
            blow up !!!
        """

        when:
        MyCustomModel model = succeeds {
            action(new CustomModelAction())
                .withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
                .run()
        }

        then:
        model.paths == [":", ":included"]
    }

    def "basic project w/ included build - broken included build settings file and build script - strict mode - build action"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = file("included")
        def includedSettings = included.file("settings.gradle.kts") << """
            boom !!!
        """
        included.file("build.gradle.kts") << """
            blow up !!!
        """

        when:
        fails {
            action(new CustomModelAction()).run()
        }

        then:
        def e = thrown(BuildActionFailureException)
        e.cause.message.contains(includedSettings.absolutePath)

        failure.assertHasDescription("Script compilation error")
    }

    static class MyCustomModel implements Serializable {

        Map<File, KotlinDslScriptModel> scriptModels
        List<ProjectIdentifier> projectIdentifiers
        List<String> paths

        MyCustomModel(
            Map<File, KotlinDslScriptModel> scriptModels,
            List<ProjectIdentifier> projectIdentifiers,
            List<String> paths
        ) {
            this.scriptModels = scriptModels
            this.projectIdentifiers = projectIdentifiers
            this.paths = paths
        }

    }

    static class CustomModelAction implements BuildAction<MyCustomModel>, Serializable {

        @Override
        MyCustomModel execute(BuildController controller) {
            GradleBuild build = controller.getModel(GradleBuild.class)
            KotlinDslScriptsModel buildScriptModel = controller.getModel(KotlinDslScriptsModel.class)

            def paths = Stream.concat(Stream.of(build), build.includedBuilds.stream())
                .flatMap(b -> b.projects.stream())
                .map(p -> p.buildTreePath)
                .collect(Collectors.toList())

            def identifier = build.projects.collect { project ->
                project.projectIdentifier
            }

            // Build your custom model
            return new MyCustomModel(
                buildScriptModel.scriptModels,
                identifier,
                paths
            )
        }

    }

}
