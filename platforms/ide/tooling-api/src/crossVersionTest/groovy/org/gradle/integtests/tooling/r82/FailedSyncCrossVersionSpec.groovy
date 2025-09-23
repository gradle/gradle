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
import org.gradle.internal.Pair
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

import static java.util.Collections.emptyList

@ToolingApiVersion('>=8.2') // Since our action uses `buildTreePath`
@TargetGradleVersion('>=8.2')
class FailedSyncCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`
    }

    def "basic build - broken settings file"() {
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

    def "basic build - broken build file"() {
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
        matchesScriptModels(model,
                Pair.of("settings.gradle.kts", emptyList()),
                Pair.of("build.gradle.kts", Collections.singletonList(".*Build file.*build\\.gradle\\.kts.*Script compilation error.*"))
        )

        // TODO: if a plugin is applied in the broken build file, do the classpaths in the script models reflect that (ie. contain plugin classes?)
    }

    def "basic build w/ included build - broken build file in included build"() {
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
        matchesScriptModels(model,
                Pair.of("settings.gradle.kts", emptyList()),
                Pair.of("included/settings.gradle.kts", emptyList()),
                Pair.of("included/build.gradle.kts", Collections.singletonList(".*Build file.*build\\.gradle\\.kts.*Script compilation error.*")),
        )

        // TODO: if a plugin is applied in the broken build file, do the classpaths in the script models reflect that (ie. contain plugin classes?)
    }

    // TODO: different tests for a build file blowing up in the body or in the plugins block

    // TODO: what if there is a convention plugin blowing up?

    def "basic build w/ included build - broken settings and build file in included build"() {
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

        failure.assertHasDescription("Script compilation error") // TODO: info about the main build would be nice
    }

    void matchesScriptModels(MyCustomModel model, Pair<String, List<String>>... expected) {
        def scriptModels = model.scriptModels
        assert scriptModels.size() == expected.size()

        for (Pair<String, List<String>> expectedElement : expected) {
            def expectedFile = new File(projectDir, expectedElement.left)
            def scriptModel = scriptModels.get(expectedFile)
            assert scriptModel != null
            matchScriptModelExceptions(scriptModel, expectedElement.right)
        }
    }

    void matchScriptModelExceptions(KotlinDslScriptModel scriptModel, List<String> expected) {
        def exceptions = scriptModel.exceptions
        assert exceptions.size() == expected.size()

        for (int i = 0; i < expected.size(); i++) {
            assert Pattern.compile(expected.get(i), Pattern.DOTALL).matcher(exceptions.get(i)).matches()
        }
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

            Map<File, KotlinDslScriptModel> scriptModels = new HashMap<>()

            KotlinDslScriptsModel buildScriptModel = controller.getModel(KotlinDslScriptsModel.class)
            scriptModels.putAll(buildScriptModel.scriptModels)

            def paths = Stream.concat(Stream.of(build), build.includedBuilds.stream())
                .flatMap(b -> b.projects.stream())
                .map(p -> p.buildTreePath)
                .collect(Collectors.toList())

            def identifier = build.projects.collect { project ->
                project.projectIdentifier
            }

            def includedBuilds = build.getIncludedBuilds()
            for (GradleBuild includedBuild : includedBuilds) {
                KotlinDslScriptsModel includedBuildScriptModel = controller.getModel(includedBuild, KotlinDslScriptsModel.class)
                scriptModels.putAll(includedBuildScriptModel.scriptModels)
            }

            // Build your custom model
            return new MyCustomModel(
                scriptModels,
                identifier,
                paths
            )
        }

    }

}
