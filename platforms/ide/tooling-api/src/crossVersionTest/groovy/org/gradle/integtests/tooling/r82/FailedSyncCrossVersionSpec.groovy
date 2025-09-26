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

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.internal.Pair
import org.gradle.test.fixtures.dsl.GradleDsl
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

import static java.util.Collections.singletonList

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

    def "basic build - broken build file - intact plugins block"() {
        given:
        settingsKotlinFile << """
            dependencyResolutionManagement {
                repositories {
                    ${RepoScriptBlockUtil.gradlePluginRepositoryDefinition(GradleDsl.KOTLIN)}
                }
            }
            rootProject.name = "root"
        """
        buildKotlinFile << """
            plugins { `kotlin-dsl` }
            
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
        assertHasScriptModelForFiles(model, "settings.gradle.kts", "build.gradle.kts")
        assertHasErrorsInScriptModels(model, Pair.of("build.gradle.kts", singletonList(".*Build file.*build\\.gradle\\.kts.*Script compilation error.*")))
        assertHasJarsInScriptModelClasspath(model, "build.gradle.kts", "gradle-kotlin-dsl-plugins")
    }

    def "basic build - broken build file - broken plugins block"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildKotlinFile << """
            plugins { blow up !!! }
        """

        when:
        MyCustomModel model = succeeds {
            action(new CustomModelAction())
                    .withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
                    .run()
        }

        then:
        model.paths == [":"]
        assertHasScriptModelForFiles(model, "settings.gradle.kts", "build.gradle.kts")
        assertHasErrorsInScriptModels(model, Pair.of("build.gradle.kts", singletonList(".*Build file.*build\\.gradle\\.kts.*Script compilation error.*")))
        assertHasJarsInScriptModelClasspath(model, "build.gradle.kts", "gradle-api")
    }

    def "basic build w/ included build - broken build file in included build - intact plugins block"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = file("included")
        included.file("settings.gradle.kts") << """
            dependencyResolutionManagement {
                repositories {
                    ${RepoScriptBlockUtil.gradlePluginRepositoryDefinition(GradleDsl.KOTLIN)}
                }
            }
            rootProject.name = "included"
        """
        included.file("build.gradle.kts") << """
            plugins { `kotlin-dsl` }
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
        assertHasScriptModelForFiles(model, "settings.gradle.kts", "included/settings.gradle.kts", "included/build.gradle.kts")
        assertHasErrorsInScriptModels(model, Pair.of("included/build.gradle.kts", singletonList(".*Build file.*build\\.gradle\\.kts.*Script compilation error.*")))
        assertHasJarsInScriptModelClasspath(model, "included/build.gradle.kts", "gradle-kotlin-dsl-plugins")
    }

    def "basic build w/ included build - broken build file in included build - broken plugins block"() {
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
            plugins { blow up !!! }
        """

        when:
        MyCustomModel model = succeeds {
            action(new CustomModelAction())
                    .withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
                    .run()
        }

        then:
        model.paths == [":", ":included"]
        assertHasScriptModelForFiles(model, "settings.gradle.kts", "included/settings.gradle.kts", "included/build.gradle.kts")
        assertHasErrorsInScriptModels(model, Pair.of("included/build.gradle.kts", singletonList(".*Build file.*build\\.gradle\\.kts.*Script compilation error.*")))
        assertHasJarsInScriptModelClasspath(model, "included/build.gradle.kts", "gradle-api")
    }

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

    void assertHasScriptModelForFiles(MyCustomModel model, String... expectedFiles) {
        def scriptModels = model.scriptModels
        assert scriptModels.size() == expectedFiles.size(): "Expected ${expectedFiles.size()} script models, but got ${scriptModels.size()} "

        for (String expectedFile : expectedFiles) {
            assert scriptModels.containsKey(new File(projectDir, expectedFile)): "No script model for file $expectedFile"
        }
    }

    void assertHasErrorsInScriptModels(MyCustomModel model, Pair<String, List<String>>... expected) {
        def scriptModels = new HashMap<>(model.scriptModels)

        for (Pair<String, List<String>> expectedElement : expected) {
            def expectedFile = new File(projectDir, expectedElement.left)
            def scriptModel = scriptModels.remove(expectedFile)
            assert scriptModel != null: "Script model for file ${expectedElement.left} not available"
            matchScriptModelExceptions(scriptModel, expectedElement.right)
        }

        for (Map.Entry<File, KotlinDslScriptModel> entry : scriptModels.entrySet()) {
            assert entry.getValue().exceptions.isEmpty(): "Unexpected errors in script model for file ${entry.key}"
        }
    }

    private static void matchScriptModelExceptions(KotlinDslScriptModel scriptModel, List<String> expected) {
        def exceptions = scriptModel.exceptions
        assert exceptions.size() == expected.size(): "Expected ${expected.size()} exceptions, but got ${exceptions.size()}"

        for (int i = 0; i < expected.size(); i++) {
            def exception = exceptions.get(i)
            def expectedPattern = expected.get(i)
            assert Pattern.compile(expectedPattern, Pattern.DOTALL).matcher(exception).matches(): "Exception \"${exception}\" doesn't match expected pattern \"${expectedPattern}\""
        }
    }

    void assertHasJarsInScriptModelClasspath(MyCustomModel model, String expectedFile, String... expectedJars) {
        def scriptModel = model.scriptModels.get(new File(projectDir, expectedFile))
        assert scriptModel != null: "Expected script model for file $expectedFile, but there wasn't one"

        def jarFilesInClasspath = scriptModel.classPath.stream()
                .filter { it.isFile() }
                .map { it.name }
                .filter { it.endsWith(".jar") }
                .collect(Collectors.toList())

        for (String expectedJar : expectedJars) {
            assert jarFilesInClasspath.stream().filter {it.startsWith(expectedJar)}.findFirst().isPresent() :
                    "Expected jar named $expectedJar in the script model classpath for file $expectedFile, " +
                    "but it wasn't there: ${jarFilesInClasspath.stream().collect(Collectors.joining("\n\t", "\n\t", ""))}"
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
