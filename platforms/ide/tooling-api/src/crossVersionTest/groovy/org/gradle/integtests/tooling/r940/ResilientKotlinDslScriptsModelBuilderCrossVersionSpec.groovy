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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.internal.Pair
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.BuildException
import org.gradle.tooling.Failure
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.util.internal.ToBeImplemented

import java.util.function.Function
import java.util.regex.Pattern
import java.util.stream.Collectors

import static org.gradle.integtests.tooling.r940.ResilientKotlinDslScriptsModelBuilderCrossVersionSpec.KotlinModelAction.QueryStrategy
import static org.gradle.integtests.tooling.r940.ResilientKotlinDslScriptsModelBuilderCrossVersionSpec.KotlinModelAction.QueryStrategy.INCLUDED_BUILDS_FIRST
import static org.gradle.integtests.tooling.r940.ResilientKotlinDslScriptsModelBuilderCrossVersionSpec.KotlinModelAction.QueryStrategy.ROOT_PROJECT_FIRST

@ToolingApiVersion('>=9.3.0')
@TargetGradleVersion('>=9.4.0')
class ResilientKotlinDslScriptsModelBuilderCrossVersionSpec extends ToolingApiSpecification {

    TestFile initScriptFile
    KotlinModelCollector modelCollector

    def setup() {
        modelCollector = new KotlinModelCollector()
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`

        initScriptFile = file("init.gradle")
        initScriptFile << """
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import javax.inject.Inject
            gradle.lifecycle.beforeProject {
                it.plugins.apply(CustomPlugin)
            }
            class StartParametersModel implements Serializable {
                String getValue() { 'greetings' }
            }
            class CustomThing implements Serializable {
            }
            class SetupStartParametersBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == '${StartParametersModel.name}'
                }
                Object buildAll(String modelName, Project project) {
                    def tasks = new HashSet<String>(project.gradle.startParameter.taskNames)
                    tasks.add("prepareKotlinBuildScriptModel")
                    tasks.add("printHelloTask")
                    project.gradle.startParameter.setTaskNames(tasks)
                    return new StartParametersModel()
                }
            }
            class CustomPlugin implements Plugin<Project> {
                @Inject
                CustomPlugin(ToolingModelBuilderRegistry registry) {
                    registry.register(new SetupStartParametersBuilder())
                }
                public void apply(Project project) {
                    project.tasks.register("printHelloTask") {
                        doLast {
                            println "Hello from a task"
                        }
                    }
                    println "Registered SetupStartParametersBuilder for project: " + (project != null ? project.name : "<no project>")
                }
            }
            """.stripIndent()
    }

    def "no failure build: resilient model is equal to non-resilient model"() {
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
        """

        when:
        def original = succeeds {
            originalModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        original

        when:
        def resilientModels = succeeds {
            resilientModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        def originalScripts = original.scriptModels.keySet()
        def resilientScripts = resilientModels.scriptModels.keySet()
        resilientScripts.size() == originalScripts.size()
        for (File scriptFile : originalScripts) {
            def modelAssert = new ComparingModelAssert(scriptFile, resilientModels, original)
            modelAssert.assertBothModelsExist()
            modelAssert.assertClassPathsAreEqual()
            modelAssert.assertImplicitImportsAreEqualIgnoringAccessors()
        }
        resilientModels.failures.isEmpty()
    }

    def "no failure in multi-project build: resilient model is equal to non-resilient model"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b", "c", "d")
        """

        file("a/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """
        file("b/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """
        file("c/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """
        file("d/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """

        when:
        def original = succeeds {
            originalModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        original

        when:
        def resilientModels = succeeds {
            resilientModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        def originalScripts = original.scriptModels.keySet()
        def resilientScripts = resilientModels.scriptModels.keySet()
        resilientScripts.size() == originalScripts.size()
        for (File scriptFile : original.scriptModels.keySet()) {
            def modelAssert = new ComparingModelAssert(scriptFile, resilientModels, original)
            modelAssert.assertBothModelsExist()
            modelAssert.assertClassPathsAreEqual()
            modelAssert.assertImplicitImportsAreEqualIgnoringAccessors()
        }
        resilientModels.failures.isEmpty()
    }

    def "compilation failure in project build script: settings and project scripts model is returned"() {
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
        fails {
            resilientModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains("Script compilation error")
        def model = modelCollector.model
        assertHasScriptModelForFiles(model, "settings.gradle.kts", "build.gradle.kts")
        assertHasErrorsInScriptModels(model,
            Pair.of(".", ".*Build file.*build\\.gradle\\.kts.*Script compilation error.*"))
        assertHasJarsInScriptModelClasspath(model, "build.gradle.kts", "gradle-kotlin-dsl-plugins")
    }

    def "compilation failure in project plugins block: settings and project scripts model is returned"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildKotlinFile << """
            plugins { blow up !!! }
        """

        when:
        fails {
            resilientModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains("Script compilation error")
        def model = modelCollector.model
        assertHasScriptModelForFiles(model, "settings.gradle.kts", "build.gradle.kts")
        assertHasErrorsInScriptModels(model,
            Pair.of(".", ".*Build file.*build\\.gradle\\.kts.*Script compilation error.*"))
        assertHasJarsInScriptModelClasspath(model, "build.gradle.kts", "gradle-api")
    }

    def "compilation failure in included build project script body: root build and included build model is returned"() {
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
        fails {
            resilientModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains("Script compilation error")
        def model = modelCollector.model
        assertHasScriptModelForFiles(model, "settings.gradle.kts", "included/settings.gradle.kts", "included/build.gradle.kts")
        assertHasErrorsInScriptModels(model,
            Pair.of(".", ".*Build file.*build\\.gradle\\.kts.*Script compilation error.*"),
            Pair.of("included", ".*Build file.*build\\.gradle\\.kts.*Script compilation error.*"))
        assertHasJarsInScriptModelClasspath(model, "included/build.gradle.kts", "gradle-kotlin-dsl-plugins")
    }

    def "compilation failure in included build project plugins block: root build and included build model is returned"() {
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
        fails {
            resilientModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains("Script compilation error")
        def model = modelCollector.model
        assertHasScriptModelForFiles(model, "settings.gradle.kts", "included/settings.gradle.kts", "included/build.gradle.kts")
        assertHasErrorsInScriptModels(model,
            Pair.of(".", ".*Build file.*build\\.gradle\\.kts.*Script compilation error.*"),
            Pair.of("included", ".*Build file.*build\\.gradle\\.kts.*Script compilation error.*"))
        assertHasJarsInScriptModelClasspath(model, "included/build.gradle.kts", "gradle-api")
    }

    def "#description failure in main build subproject: resilient model is equal to non-resilient model except accessors with #queryStrategy"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b", "c", "d")
        """

        file("a/build.gradle.kts") << """
            plugins {
                id("java")
            }

        """
        file("b/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """
        def c = file("c/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """
        def d = file("d/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """

        when:
        def original = succeeds {
            originalModel(it, queryStrategy)
        }

        then:
        original

        when:
        c << """$breakage"""
        fails {
            resilientModel(it, queryStrategy)
        }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains(expectedFailure)
        def resilientModels = modelCollector.model
        def originalScripts = original.scriptModels.keySet()
        def resilientScripts = resilientModels.scriptModels.keySet()
        resilientScripts.size() == originalScripts.size()
        for (File scriptFile : original.scriptModels.keySet()) {
            def modelAssert = new ComparingModelAssert(scriptFile, resilientModels, original)
            modelAssert.assertBothModelsExist()
            if (scriptFile == d) {
                // In this case we don't have accessors in the classpath
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeOriginalEntries { !it.contains("/accessors/") }
                modelAssert.assertImplicitImportsAreEqualIgnoringAccessors()
            } else {
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeEntries { !it.contains("/accessors/") }
                modelAssert.assertImplicitImportsAreEqualIgnoringAccessors()
            }
        }
        resilientModels.failures.size() == 1
        expectFailureToContain(resilientModels.failures[settingsKotlinFile.parentFile], "c" + File.separatorChar + "build.gradle.kts' line: 5")
        expectFailureToContain(resilientModels.failures[settingsKotlinFile.parentFile], expectedFailure)

        where:
        description                | breakage                                     | expectedFailure  | queryStrategy
        "scripts evaluation fails" | "throw RuntimeException(\"Failing script\")" | "Failing script" | ROOT_PROJECT_FIRST
        "scripts evaluation fails" | "throw RuntimeException(\"Failing script\")" | "Failing script" | INCLUDED_BUILDS_FIRST
        "script compilation fails" | "broken !!!"                                 | "broken !!!"     | ROOT_PROJECT_FIRST
        "script compilation fails" | "broken !!!"                                 | "broken !!!"     | INCLUDED_BUILDS_FIRST
    }

    def "runtime failure in project convention plugin: resilient model is equal to non-resilient model except accessors and build-logic jar #queryStrategy\""() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b", "c")
            includeBuild("build-logic")
        """

        def included = file("build-logic")
        included.file("settings.gradle.kts") << """
            rootProject.name = "build-logic"

            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
        """
        included.file("build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }

            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        """
        def projectPlugin = included.file("src/main/kotlin/build-logic.gradle.kts") << """"""
        file("a/build.gradle.kts") << """
            plugins {
                id("java")
            }

        """
        def b = file("b/build.gradle.kts") << """
            plugins {
                id("build-logic")
            }
        """
        def c = file("c/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """


        when:
        def original = succeeds {
            originalModel(it, queryStrategy)
        }

        then:
        original

        when:
        projectPlugin << "throw RuntimeException(\"Failing script\")"
        fails {
            resilientModel(it, queryStrategy)
        }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains("b" + File.separatorChar + "build.gradle.kts' line: 2")
        def resilientModels = modelCollector.model
        def originalScripts = original.scriptModels.keySet()
        def resilientScripts = resilientModels.scriptModels.keySet()
        resilientScripts.size() == originalScripts.size()
        for (File scriptFile : original.scriptModels.keySet()) {
            def modelAssert = new ComparingModelAssert(scriptFile, resilientModels, original)
            modelAssert.assertBothModelsExist()
            if (scriptFile == b) {
                // For some reason the build logic and accessors are included but different
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeEntries { !it.contains("/accessors/") && !it.contains("/build-logic.jar") }
                modelAssert.assertResilientModelContainsClassPathEntriesWithPath("/accessors/")
                modelAssert.assertResilientModelContainsClassPathEntriesWithPath("/build-logic.jar")
                modelAssert.assertImplicitImportsAreEqualIgnoringAccessors()
            } else if (scriptFile == c) {
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeOriginalEntries { !it.contains("/accessors/") }
                modelAssert.assertImplicitImportsAreEqualIgnoringAccessors()
            } else {
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeEntries { !it.contains("/accessors/") }
                modelAssert.assertImplicitImportsAreEqualIgnoringAccessors()
            }
        }
        resilientModels.failures.size() == 1
        expectFailureToContain(resilientModels.failures[settingsKotlinFile.parentFile], "b" + File.separatorChar + "build.gradle.kts' line: 2")
        expectFailureToContain(resilientModels.failures[settingsKotlinFile.parentFile], "Failing script")

        where:
        queryStrategy << [ROOT_PROJECT_FIRST, INCLUDED_BUILDS_FIRST]
    }

    def "compilation failure in project convention plugin: resilient model is equal to non-resilient model except accessors and build-logic jar #queryStrategy"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b", "c")
            includeBuild("build-logic")
        """

        def included = file("build-logic")
        included.file("settings.gradle.kts") << """
            rootProject.name = "build-logic"

            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
        """
        included.file("build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }

            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        """
        def projectPlugin = included.file("src/main/kotlin/build-logic.gradle.kts") << """"""
        file("a/build.gradle.kts") << """
            plugins {
                id("java")
            }

        """
        def b = file("b/build.gradle.kts") << """
            plugins {
                id("build-logic")
            }
        """
        def c = file("c/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """


        when:
        def original = succeeds {
            originalModel(it, queryStrategy)
        }

        then:
        original

        when:
        projectPlugin << """ broken !!! """
        fails {
            resilientModel(it, queryStrategy)
        }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains("Execution failed for task ':build-logic:compileKotlin'")
        def resilientModels = modelCollector.model
        def originalScripts = original.scriptModels.keySet()
        def resilientScripts = resilientModels.scriptModels.keySet()
        resilientScripts.size() == originalScripts.size()
        for (File scriptFile : original.scriptModels.keySet()) {
            def modelAssert = new ComparingModelAssert(scriptFile, resilientModels, original)
            modelAssert.assertBothModelsExist()
            if (scriptFile == b) {
                // In this case we don't have accessors and build-logic in the classpath
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeOriginalEntries { !it.contains("/accessors/") && !it.contains("/build-logic.jar") }
                modelAssert.assertImplicitImportsAreEqualIgnoringAccessors()
            } else if (scriptFile == c) {
                // In this case we don't have accessors, since this project is not configured
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeOriginalEntries { !it.contains("/accessors/") }
                modelAssert.assertImplicitImportsAreEqualIgnoringAccessors()
            } else {
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeEntries { !it.contains("/accessors/") }
                modelAssert.assertImplicitImportsAreEqualIgnoringAccessors()
            }
        }
        def actualNoOfFailures = resilientModels.failures.size()
        assert actualNoOfFailures == 1: "Expected 1 failures, but had ${actualNoOfFailures}"
        expectFailureToContain(resilientModels.failures[settingsKotlinFile.parentFile], "A problem occurred configuring project ':b'.")

        where:
        queryStrategy << [ROOT_PROJECT_FIRST, INCLUDED_BUILDS_FIRST]
    }

    @ToBeImplemented
    def "compilation failure in root settings: no model is returned"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """

        when:
        fails {
            resilientModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains("Script compilation error")
        def model = modelCollector.model
        model.scriptModels.isEmpty()
        assertHasErrorsInScriptModels(model,
            Pair.of(".", ".*Settings file.*settings\\.gradle\\.kts.*Script compilation error.*"))
    }

    @ToBeImplemented
    def "compilation failure in included build settings script: no model is returned"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = file("included")
        included.file("settings.gradle.kts") << """
            boom !!!
        """
        included.file("build.gradle.kts") << """
            blow up !!!
        """

        when:
        fails {
            resilientModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains("Script compilation error")
        def model = modelCollector.model
        model.scriptModels.isEmpty()
        assertHasErrorsInScriptModels(model,
            Pair.of(".", ".*Settings file.*settings\\.gradle\\.kts.*Script compilation error.*"),
            Pair.of("included", ".*Settings file.*settings\\.gradle\\.kts.*Script compilation error.*"))
    }

    def "compilation failure in settings convention plugin: model is returned for included build but not for main build"() {
        given:
        settingsKotlinFile << """
            pluginManagement {
                includeBuild("build-logic")
            }
            rootProject.name = "root"
            plugins {
                id("build-logic")
            }
        """

        def included = file("build-logic")
        included.file("settings.gradle.kts") << """
            rootProject.name = "build-logic"

            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
        """
        included.file("build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }

            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        """
        def settingsPlugin = included.file("src/main/kotlin/build-logic.settings.gradle.kts") << ""

        when:
        def original = succeeds {
            originalModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        original

        when:
        settingsPlugin << """ broken !!! """
        fails {
            resilientModel(it, ROOT_PROJECT_FIRST)
        }

        then:
        def e = thrown(BuildException)
        e.cause.message.contains("Execution failed for task")
        def model = modelCollector.model
        assertHasScriptModelForFiles(model, "build-logic/settings.gradle.kts", "build-logic/build.gradle.kts", "build-logic/src/main/kotlin/build-logic.settings.gradle.kts")
        assertHasErrorsInScriptModels(model,
            Pair.of(".", ".*Execution failed for task ':build-logic:compileKotlin.*"))
    }

    def "resilient Kotlin DSL can be queried with null target"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildFileKts << """
            broken !!!
        """

        when:
        def model = succeeds {
            action(new KotlinModelOnNullTargetAction())
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true")
                .run()
        }

        then:
        assertHasScriptModelForFiles(model, "settings.gradle.kts", "build.gradle.kts")
        assertHasErrorsInScriptModels(model,
            Pair.of(".", ".*Build file.*build\\.gradle\\.kts.*Script compilation error.*"))
    }

    def "no resilient Kotlin DSL model is returned if settings fails with null target"() {
        given:
        settingsKotlinFile << """
            broken !!!
        """

        when:
        def model = succeeds {
            action(new KotlinModelOnNullTargetAction())
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true")
                .run()
        }

        then:
        model.scriptModels.isEmpty()
        assertHasErrorsInScriptModels(model,
            Pair.of(".", ".*Settings file.*settings\\.gradle\\.kts.*Script compilation error.*"))
    }

    void assertHasScriptModelForFiles(KotlinModel model, String... expectedFiles) {
        def scriptModels = model.scriptModels
        def scriptModelsFiles = scriptModels.keySet()
        assert scriptModelsFiles.size() == expectedFiles.size(): "Expected ${expectedFiles.size()} script models, but got ${scriptModels.size()} "

        for (String expectedFile : expectedFiles) {
            assert scriptModels.containsKey(new File(projectDir, expectedFile)): "No script model for file $expectedFile"
        }
    }

    void assertHasErrorsInScriptModels(KotlinModel model, Pair<String, String>... expected) {
        assert model.failures.size() == expected.size(): "Expected ${expected.size()} failures, but got ${model.failures.size()}"
        def failures = new HashMap<>(model.failures)

        for (Pair<String, String> expectedElement : expected) {
            def buildRootDir = new File(new File(projectDir, expectedElement.left).canonicalPath)
            def failure = failures.remove(buildRootDir)
            assert failure: "Failures for build root ${expectedElement.left} not available"
            matchProjectFailure(failure, expectedElement.right)
        }

        assert failures.isEmpty(): "Unexpected failures for build roots: ${failures.keySet()}"
    }

    private static void matchProjectFailure(String failureMessage, String expectedPattern) {
        def pattern = Pattern.compile(expectedPattern, Pattern.DOTALL)
        def matcher = pattern.matcher(failureMessage)
        assert matcher.matches(): "Exception \"${failureMessage}\" doesn't match expected pattern \"${expectedPattern}\""
    }

    void assertHasJarsInScriptModelClasspath(KotlinModel model, String expectedFile, String... expectedJars) {
        def scriptModel = model.scriptModels.get(new File(projectDir, expectedFile))
        assert scriptModel != null: "Expected script model for file $expectedFile, but there wasn't one"

        def jarFilesInClasspath = scriptModel.classPath.stream()
            .filter { it.isFile() }
            .map { it.name }
            .filter { it.endsWith(".jar") }
            .collect(Collectors.toList())

        for (String expectedJar : expectedJars) {
            assert jarFilesInClasspath.stream().filter { it.startsWith(expectedJar) }.findFirst().isPresent():
                "Expected jar named $expectedJar in the script model classpath for file $expectedFile, " +
                    "but it wasn't there: ${jarFilesInClasspath.stream().collect(Collectors.joining("\n\t", "\n\t", ""))}"
        }
    }

    static void expectFailureToContain(String actualFailure, String expectedFragment) {
        assert actualFailure.contains(expectedFragment):
            "Failure expected to contain \"${expectedFragment}\", but was \"\n${actualFailure}\n\" instead!"
    }

    private static void queryResilientKotlinDslScriptsModel(BuildController controller, GradleBuild build, Model target, Map<File, KotlinDslScriptModel> scriptModels, Map<File, Failure> failures) {
        def modelResult = controller.fetch(target, KotlinDslScriptsModel.class)

        assert modelResult.failures.size() <= 1: "Expected a single failure, but got multiple ones"
        def failure = modelResult.failures.stream().findAny()
        if (failure.isPresent()) {
            failures[build.buildIdentifier.rootDir] = failure.get()
        }

        if (modelResult.model != null) {
            scriptModels.putAll(modelResult.model.scriptModels)
        }
    }

    private static void queryBasicKotlinDslScriptsModel(BuildController controller, GradleBuild build, Map<File, KotlinDslScriptModel> scriptModels) {
        KotlinDslScriptsModel buildScriptModel = controller.getModel(build.rootProject, KotlinDslScriptsModel.class)
        scriptModels.putAll(buildScriptModel.scriptModels)
    }


    KotlinModel resilientModel(ProjectConnection conn, QueryStrategy queryStrategy) {
        return KotlinModelAction.model(conn, true, queryStrategy, initScriptFile, modelCollector)
    }

    KotlinModel originalModel(ProjectConnection conn, QueryStrategy queryStrategy) {
        return KotlinModelAction.model(conn, false, queryStrategy, initScriptFile, modelCollector)
    }

    static class KotlinModel implements Serializable {

        final Map<File, KotlinDslScriptModel> scriptModels
        final Map<File, String> failures

        KotlinModel(Map<File, KotlinDslScriptModel> scriptModels, Map<File, Failure> failures) {
            this.scriptModels = scriptModels
            this.failures = failures.collectEntries { key, value -> [key, value.description] }
        }
    }

    static class KotlinModelAction implements BuildAction<KotlinModel>, Serializable {

        static enum QueryStrategy {
            ROOT_PROJECT_FIRST,
            INCLUDED_BUILDS_FIRST
        }

        final QueryStrategy queryStrategy
        final boolean resilient

        KotlinModelAction(QueryStrategy queryStrategy, boolean resilient) {
            this.queryStrategy = queryStrategy
            this.resilient = resilient
        }

        @Override
        KotlinModel execute(BuildController controller) {
            GradleBuild rootBuild
            if (resilient) {
                rootBuild = controller.fetch(GradleBuild.class).model
            } else {
                rootBuild = controller.getModel(GradleBuild.class)
            }
            Map<File, KotlinDslScriptModel> scriptModels = [:]
            Map<File, Failure> failures = [:]

            if (queryStrategy == ROOT_PROJECT_FIRST) {
                queryKotlinDslScriptsModel(controller, rootBuild, scriptModels, failures)
                for (GradleBuild build : rootBuild.editableBuilds) {
                    queryKotlinDslScriptsModel(controller, build, scriptModels, failures)
                }
            } else if (queryStrategy == INCLUDED_BUILDS_FIRST) {
                for (GradleBuild build : rootBuild.editableBuilds) {
                    queryKotlinDslScriptsModel(controller, build, scriptModels, failures)
                }
                queryKotlinDslScriptsModel(controller, rootBuild, scriptModels, failures)
            }

            return new KotlinModel(scriptModels, failures)
        }

        private void queryKotlinDslScriptsModel(BuildController controller, GradleBuild build, Map<File, KotlinDslScriptModel> scriptModels, Map<File, Failure> failures) {
            if (resilient) {
                queryResilientKotlinDslScriptsModel(controller, build, build.rootProject, scriptModels, failures)
            } else {
                queryBasicKotlinDslScriptsModel(controller, build, scriptModels)
            }
        }

        private static KotlinModel model(ProjectConnection conn, boolean resilient, QueryStrategy queryStrategy, File initScript, IntermediateResultHandler<KotlinModel> modelHandler) {
            def model = null

            Iterable<String> arguments = ["--init-script=${initScript.absolutePath}"]
            if (resilient) {
                arguments += "-Dorg.gradle.internal.resilient-model-building=true"
            }

            conn.action()
                    .projectsLoaded(new SetStartParameterAction(resilient)) {
                        it.contains("successful") || it.contains("unsuccessful")
                    }
                    .buildFinished(new KotlinModelAction(queryStrategy, resilient)) {
                        modelHandler.onComplete(it)
                        model = it
                    }.build()
                    .forTasks([])
                    .withArguments(*arguments)
                    .run()
            return model
        }
    }

    static class SetStartParameterAction implements BuildAction<String>, Serializable {

        private final boolean resilient;

        SetStartParameterAction(boolean resilient) {
            this.resilient = resilient
        }

        @Override
        String execute(BuildController controller) {
            if (resilient) {
                def gradleBuild = controller.fetch(GradleBuild).model
                if (gradleBuild) {
                    def result = controller.fetch(gradleBuild.rootProject, StartParametersModel)
                    return result.failures.isEmpty() ? "successful" : "unsuccessful"
                }
                return "unsuccessful"
            } else {
                def gradleBuild = controller.getModel(GradleBuild)
                def result = controller.getModel(gradleBuild.rootProject, StartParametersModel)
                return result
            }
        }
    }

    static class KotlinModelOnNullTargetAction implements BuildAction<KotlinModel>, Serializable {
        @Override
        KotlinModel execute(BuildController controller) {
            GradleBuild build = controller.fetch(GradleBuild.class).model
            assert build != null
            Map<File, KotlinDslScriptModel> scriptModels = [:]
            Map<File, Failure> failures = [:]
            queryResilientKotlinDslScriptsModel(controller, build, null, scriptModels, failures)
            return new KotlinModel(scriptModels, failures)
        }
    }

    static class KotlinModelCollector implements IntermediateResultHandler<KotlinModel> {
        KotlinModel model

        @Override
        void onComplete(KotlinModel result) {
            this.model = result
        }
    }

    static class ComparingModelAssert {

        KotlinModel resilientCustomModel
        KotlinModel originalCustomModel
        KotlinDslScriptModel resilientModel
        KotlinDslScriptModel originalModel
        File scriptFile

        ComparingModelAssert(File scriptFile, KotlinModel resilientModel, KotlinModel originalModel) {
            this.scriptFile = scriptFile
            this.resilientCustomModel = resilientModel
            this.originalCustomModel = originalModel
            this.resilientModel = resilientModel.scriptModels.get(scriptFile)
            this.originalModel = originalModel.scriptModels.get(scriptFile)
        }

        ComparingModelAssert assertBothModelsExist() {
            assert originalModel: "Original model for script ${scriptFile} is missing, scripts that have original model are:\n" +
                " ${originalCustomModel.scriptModels.keySet()}"
            assert resilientModel: "Resilient model for script ${scriptFile} is missing, scripts that have resilient model are:\n" +
                " ${resilientCustomModel.scriptModels.keySet()}"
            return this
        }


        ComparingModelAssert assertClassPathsAreEqual() {
            assert resilientModel.classPath == originalModel.classPath: "Class paths are not equal for script ${scriptFile}:\n" +
                " - Resilient classPath: ${resilientModel.classPath}\n" +
                " - Original classPath:  ${originalModel.classPath}"
            return this
        }

        ComparingModelAssert assertClassPathsAreEqualIfIgnoringSomeOriginalEntries(Function<String, Boolean> filter) {
            def filteredOriginalClassPath = originalModel.classPath.findAll { filter.apply(TextUtil.normaliseFileSeparators(it.absolutePath)) }
            assert resilientModel.classPath == filteredOriginalClassPath: "Class paths are not equal after filtering original entries for script ${scriptFile}:\n" +
                " - Resilient classPath:         ${resilientModel.classPath}\n" +
                " - Filtered original classPath: ${filteredOriginalClassPath}"
            return this
        }

        ComparingModelAssert assertClassPathsAreEqualIfIgnoringSomeEntries(Function<String, Boolean> filter) {
            def filteredResilient = resilientModel.classPath.findAll { filter.apply(TextUtil.normaliseFileSeparators(it.absolutePath)) }
            def filteredOriginalClassPath = originalModel.classPath.findAll { filter.apply(TextUtil.normaliseFileSeparators(it.absolutePath)) }
            assert filteredResilient == filteredOriginalClassPath: "Class paths are not equal after filtering some entries for script ${scriptFile}:\n" +
                " - Riltered resilient: ${filteredResilient}\n" +
                " - Riltered original:  ${filteredOriginalClassPath}"
            return this
        }

        ComparingModelAssert assertResilientModelContainsClassPathEntriesWithPath(String path) {
            def filteredResilient = resilientModel.classPath.findAll { TextUtil.normaliseFileSeparators(it.absolutePath).contains(path) }
            assert !filteredResilient.isEmpty(): "Resilient Class paths for script ${scriptFile} did not contain entries with path '${path}':\n" +
                " - Resilient classPath: ${resilientModel.classPath}"
            return this
        }

        ComparingModelAssert assertImplicitImportsAreEqualIgnoringAccessors() {
            def relevantResilientImports = minusAccessors(resilientModel.implicitImports)
            def relevantOriginalImports = minusAccessors(originalModel.implicitImports)
            assert relevantResilientImports == relevantOriginalImports: "Implicit imports are not equal for script ${scriptFile}:\n" +
                " - Resilient imports: $relevantResilientImports\n" +
                " - Original imports:  $relevantOriginalImports"
            return this
        }

        private static List<String> minusAccessors(List<String> implicitImports) {
            implicitImports.findAll { !it.startsWith("gradle.kotlin.dsl.plugins._") }
        }
    }
}

interface StartParametersModel {
    String getValue();
}
