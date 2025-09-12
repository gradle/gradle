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
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import java.util.stream.Collectors
import java.util.stream.Stream

@ToolingApiVersion('>=8.2')
// Since our action uses `buildTreePath`
@TargetGradleVersion('>=8.2')
class FailedSyncCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`
    }

    def "basic project - nothing broken"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildKotlinFile << """"""

        when:
        MyCustomModel model = succeeds {
            action(new CustomModelAction())
                    .withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
                    .run()
        }

        then:
        model.projectPaths == [":"]
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
        model.projectPaths == [":"]
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
        model.projectPaths == [":", ":included"]
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
        List<String> projectPaths

        MyCustomModel(List<String> projectPaths, Map<File, KotlinDslScriptModel> scriptModels) {
            this.scriptModels = scriptModels
            this.projectPaths = projectPaths
        }

    }

    static class CustomModelAction implements BuildAction<MyCustomModel>, Serializable {

        @Override
        MyCustomModel execute(BuildController controller) {
            GradleBuild build = controller.getModel(GradleBuild.class)

            /*def legacyModel = controller.getModel(KotlinDslScriptsModel.class)
            def legacyData = legacyModel.scriptModels
            println "legacyData.size() = $legacyData.size()"*/

            def resilientResult = controller.getResilientModel(KotlinDslScriptsModel.class)
            KotlinDslScriptsModel buildScriptsModel = resilientResult.model
            def data = buildScriptsModel.scriptModels

            def projectPaths = Stream.concat(Stream.of(build), build.includedBuilds.stream())
                    .flatMap(b -> b.projects.stream())
                    .map(p -> p.buildTreePath)
                    .collect(Collectors.toList())


            return new MyCustomModel(projectPaths, data)
        }

    }

}
