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

package org.gradle.integtests.tooling.r920


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.tooling.model.kotlin.dsl.ResilientKotlinDslScriptsModel
import org.gradle.util.internal.ToBeImplemented

import java.util.function.Function

import static org.gradle.integtests.tooling.r920.ResilientKotlinDslScriptsModelBuilderCrossVersionSpec.KotlinModelAction.QueryStrategy.*

@ToolingApiVersion('>=9.2')
@TargetGradleVersion('>=9.2')
class ResilientKotlinDslScriptsModelBuilderCrossVersionSpec extends ToolingApiSpecification {

    static final String GRADLE_PROJECT_FAILURE = "Failed to get GradleProject model"

    def setup() {
        settingsFile.delete()
    }

    def "returns all models if there is no exception"() {
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
            action(KotlinModelAction.originalModel(ROOT_PROJECT_FIRST)).run()
        }

        then:
        original

        when:
        def resilientModels = succeeds {
            action(KotlinModelAction.resilientModel(ROOT_PROJECT_FIRST))
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true")
                .run()
        }

        then:
        def originalScripts = original.scriptModels.keySet()
        def resilientScripts = resilientModels.scriptModels.keySet()
        resilientScripts.size() == originalScripts.size()
        for (File scriptFile : original.scriptModels.keySet()) {
            def modelAssert = new ResilientKotlinModelAssert(scriptFile, resilientModels, original)
            modelAssert.assertBothModelsExist()
            modelAssert.assertClassPathsAreEqual()
            modelAssert.assertImplicitImportsAreEqual()
        }
        resilientModels.failureMessages.isEmpty()
        resilientModels.otherModelFailures.isEmpty()
    }

    def "returns all successful and first failed script model when #description with #queryStrategy"() {
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
            action(KotlinModelAction.originalModel(queryStrategy)).run()
        }

        then:
        original

        when:
        c << """$breakage"""
        def resilientModels = succeeds {
            action(KotlinModelAction.resilientModel(queryStrategy))
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true")
                .run()
        }

        then:
        def originalScripts = original.scriptModels.keySet()
        def resilientScripts = resilientModels.scriptModels.keySet()
        resilientScripts.size() == originalScripts.size()
        for (File scriptFile : original.scriptModels.keySet()) {
            def modelAssert = new ResilientKotlinModelAssert(scriptFile, resilientModels, original)
            modelAssert.assertBothModelsExist()
            if (scriptFile == d) {
                // In this case we don't have accessors in the classpath
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeOriginalEntries { !it.contains("/accessors/") }
                modelAssert.assertImplicitImportsAreEqual()
            } else {
                modelAssert.assertClassPathsAreEqual()
                modelAssert.assertImplicitImportsAreEqual()
            }
        }
        resilientModels.failureMessages.size() == 1
        resilientModels.failureMessages[settingsKotlinFile.parentFile].contains("c/build.gradle.kts' line: 5")
        resilientModels.failureMessages[settingsKotlinFile.parentFile].contains(expectedFailure)
        resilientModels.otherModelFailures.contains(GRADLE_PROJECT_FAILURE)

        where:
        description                | breakage                                     | expectedFailure  | queryStrategy
        "scripts evaluation fails" | "throw RuntimeException(\"Failing script\")" | "Failing script" | ROOT_PROJECT_FIRST
        "scripts evaluation fails" | "throw RuntimeException(\"Failing script\")" | "Failing script" | INCLUDED_BUILDS_FIRST
        "script compilation fails" | "broken !!!"                                 | "broken !!!"     | ROOT_PROJECT_FIRST
        "script compilation fails" | "broken !!!"                                 | "broken !!!"     | INCLUDED_BUILDS_FIRST
    }

    def "returns scripts models when project convention plugin is failing with exception with #queryStrategy"() {
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
            action(KotlinModelAction.originalModel(queryStrategy)).run()
        }

        then:
        original

        when:
        projectPlugin << "throw RuntimeException(\"Failing script\")"
        def resilientModels = succeeds {
            action(KotlinModelAction.resilientModel(queryStrategy))
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true")
                .run()
        }

        then:
        def originalScripts = original.scriptModels.keySet()
        def resilientScripts = resilientModels.scriptModels.keySet()
        resilientScripts.size() == originalScripts.size()
        for (File scriptFile : original.scriptModels.keySet()) {
            def modelAssert = new ResilientKotlinModelAssert(scriptFile, resilientModels, original)
            modelAssert.assertBothModelsExist()
            if (scriptFile == b) {
                // For some reason the build logic and accessors are included but different
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeEntries { !it.contains("/accessors/") && !it.contains("/build-logic.jar") }
                modelAssert.assertResilientModelContainsClassPathEntriesWithPath("/accessors/")
                modelAssert.assertResilientModelContainsClassPathEntriesWithPath("/build-logic.jar")
                modelAssert.assertImplicitImportsAreEqual()
            } else if (scriptFile == c) {
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeOriginalEntries { !it.contains("/accessors/") }
                modelAssert.assertImplicitImportsAreEqual()
            } else {
                modelAssert.assertClassPathsAreEqual()
                modelAssert.assertImplicitImportsAreEqual()
            }
        }
        resilientModels.failureMessages.size() == 1
        resilientModels.failureMessages[settingsKotlinFile.parentFile].contains("b/build.gradle.kts' line: 2")
        resilientModels.failureMessages[settingsKotlinFile.parentFile].contains("Failing script")
        resilientModels.otherModelFailures.contains(GRADLE_PROJECT_FAILURE)

        where:
        queryStrategy << [ROOT_PROJECT_FIRST, INCLUDED_BUILDS_FIRST]
    }

    def "returns scripts models when project convention plugin is failing with compile error with #queryStrategy"() {
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
            action(KotlinModelAction.originalModel(queryStrategy)).run()
        }

        then:
        original

        when:
        projectPlugin << """ broken !!! """
        def resilientModels = succeeds {
            action(KotlinModelAction.resilientModel(queryStrategy)).withArguments("-Dorg.gradle.internal.resilient-model-building=true").run()
        }

        then:
        def originalScripts = original.scriptModels.keySet()
        def resilientScripts = resilientModels.scriptModels.keySet()
        resilientScripts.size() == originalScripts.size()
        for (File scriptFile : original.scriptModels.keySet()) {
            def modelAssert = new ResilientKotlinModelAssert(scriptFile, resilientModels, original)
            modelAssert.assertBothModelsExist()
            if (scriptFile == b) {
                // In this case we don't have accessors and build-logic in the classpath
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeOriginalEntries { !it.contains("/accessors/") && !it.contains("/build-logic.jar") }
                modelAssert.assertImplicitImportsAreEqual()
            } else if (scriptFile == c) {
                // In this case we don't have accessors, since this project is not configured
                modelAssert.assertClassPathsAreEqualIfIgnoringSomeOriginalEntries { !it.contains("/accessors/") }
                modelAssert.assertImplicitImportsAreEqual()
            } else {
                modelAssert.assertClassPathsAreEqual()
                modelAssert.assertImplicitImportsAreEqual()
            }
        }
        // At the moment the failure reporting is not consistent and depends on the order of query
        resilientModels.failureMessages.size() == numberOfFailures
        rootBuildFailure?.with {
            assert resilientModels.failureMessages[settingsKotlinFile.parentFile].contains(it)
        }
        includedBuildFailure?.with {
            assert resilientModels.failureMessages[included].contains(it)
        }
        resilientModels.otherModelFailures.contains(GRADLE_PROJECT_FAILURE)

        where:
        queryStrategy         | numberOfFailures | rootBuildFailure                               | includedBuildFailure
        ROOT_PROJECT_FIRST    | 1                | "A problem occurred configuring project ':b'." | null
        INCLUDED_BUILDS_FIRST | 2                | "A problem occurred configuring project ':b'." | "Execution failed for task ':build-logic:compileKotlin'."
    }

    @ToBeImplemented("Needs resilient GradleBuild model")
    def "returns scripts models for when settings convention plugin is broken"() {
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
        def settingsPlugin = included.file("src/main/kotlin/build-logic.settings.gradle.kts") << """
        """

        when:
        def original = succeeds {
            action(KotlinModelAction.originalModel(ROOT_PROJECT_FIRST)).run()
        }

        then:
        original

        when:
        settingsPlugin << """ broken !!! """
        fails {
            action(KotlinModelAction.resilientModel(ROOT_PROJECT_FIRST)).withArguments("-Dorg.gradle.internal.resilient-model-building=true").run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.message.startsWith("The supplied build action failed with an exception.")
    }

    static class KotlinModel implements Serializable {

        final Map<File, KotlinDslScriptModel> scriptModels
        final Map<File, String> failureMessages
        final List<String> otherModelFailures

        KotlinModel(Map<File, KotlinDslScriptModel> scriptModels, Map<File, Failure> failure, List<String> otherModelFailures) {
            this.scriptModels = scriptModels
            this.failureMessages = failure.collectEntries { k, v -> [(k): TextUtil.normaliseFileSeparators(v.description)] }
            this.otherModelFailures = otherModelFailures
        }
    }

    static class KotlinModelAction implements BuildAction<KotlinModel>, Serializable {

        static enum QueryStrategy {
            ROOT_PROJECT_FIRST,
            INCLUDED_BUILDS_FIRST
        }

        final QueryStrategy queryStrategy
        final Class<?> kotlinDslScriptModelType

        KotlinModelAction(QueryStrategy queryStrategy, Class<?> kotlinDslScriptModelType) {
            this.queryStrategy = queryStrategy
            this.kotlinDslScriptModelType = kotlinDslScriptModelType
        }

        @Override
        KotlinModel execute(BuildController controller) {
            GradleBuild gradleBuild = controller.getModel(GradleBuild.class)
            Map<File, KotlinDslScriptModel> scriptModels = [:]
            Map<File, Failure> failures = [:]
            List<String> otherModelFailures = []

            try {
                // Query also some other model to simulate IDE
                controller.getModel(GradleProject)
            } catch (Exception ignored) {
                otherModelFailures.add(GRADLE_PROJECT_FAILURE)
            }

            if (queryStrategy == ROOT_PROJECT_FIRST) {
                queryKotlinDslScriptsModel(controller, gradleBuild, scriptModels, failures)
                for (GradleBuild build : gradleBuild.includedBuilds) {
                    queryKotlinDslScriptsModel(controller, gradleBuild, scriptModels, failures)
                }
            } else if (queryStrategy == INCLUDED_BUILDS_FIRST) {
                for (GradleBuild build : gradleBuild.includedBuilds) {
                    queryKotlinDslScriptsModel(controller, build, scriptModels, failures)
                }
                queryKotlinDslScriptsModel(controller, gradleBuild, scriptModels, failures)
            }

            return new KotlinModel(
                scriptModels,
                failures,
                otherModelFailures
            )
        }

        private void queryKotlinDslScriptsModel(BuildController controller, GradleBuild build, Map<File, KotlinDslScriptModel> scriptModels, Map<File, Failure> failures) {
            if (kotlinDslScriptModelType == ResilientKotlinDslScriptsModel) {
                ResilientKotlinDslScriptsModel buildScriptModel = controller.getModel(build.rootProject, ResilientKotlinDslScriptsModel.class)
                scriptModels += buildScriptModel.model.scriptModels
                if (buildScriptModel.failure) {
                    failures[build.buildIdentifier.rootDir] = buildScriptModel.failure
                }
            } else {
                KotlinDslScriptsModel buildScriptModel = controller.getModel(build.rootProject, KotlinDslScriptsModel.class)
                scriptModels += buildScriptModel.scriptModels
            }
        }

        static KotlinModelAction resilientModel(QueryStrategy queryStrategy) {
            return new KotlinModelAction(queryStrategy, ResilientKotlinDslScriptsModel.class)
        }

        static KotlinModelAction originalModel(QueryStrategy queryStrategy) {
            return new KotlinModelAction(queryStrategy, KotlinDslScriptsModel.class)
        }
    }

    static class ResilientKotlinModelAssert {

        KotlinModel resilientCustomModel
        KotlinModel originalCustomModel
        KotlinDslScriptModel resilientModel
        KotlinDslScriptModel originalModel
        File scriptFile

        ResilientKotlinModelAssert(File scriptFile, KotlinModel resilientModel, KotlinModel originalModel) {
            this.scriptFile = scriptFile
            this.resilientCustomModel = resilientModel
            this.originalCustomModel = originalModel
            this.resilientModel = resilientModel.scriptModels.get(scriptFile)
            this.originalModel = originalModel.scriptModels.get(scriptFile)
        }

        ResilientKotlinModelAssert assertBothModelsExist() {
            if (!originalModel) {
                throw new AssertionError("Original model for script ${scriptFile} is missing, scripts that have original model are:\n" +
                    " ${originalCustomModel.scriptModels.keySet()}")
            }
            if (!resilientModel) {
                throw new AssertionError("Resilient model for script ${scriptFile} is missing, scripts that have resilient model are:\n" +
                    " ${resilientCustomModel.scriptModels.keySet()}")
            }
            return this
        }


        ResilientKotlinModelAssert assertClassPathsAreEqual() {
            if (resilientModel.classPath != originalModel.classPath) {
                throw new AssertionError("Class paths are not equal for script ${scriptFile}:\n" +
                    " - Resilient classPath: ${resilientModel.classPath}\n" +
                    " - Original classPath:  ${originalModel.classPath}")
            }
            return this
        }

        ResilientKotlinModelAssert assertClassPathsAreEqualIfIgnoringSomeOriginalEntries(Function<String, Boolean> filter) {
            def filteredOriginalClassPath = originalModel.classPath.findAll { filter.apply(TextUtil.normaliseFileSeparators(it.absolutePath)) }
            if (resilientModel.classPath != filteredOriginalClassPath) {
                throw new AssertionError("Class paths are not equal after filtering original entries for script ${scriptFile}:\n" +
                    " - Resilient classPath:         ${resilientModel.classPath}\n" +
                    " - Filtered original classPath: ${filteredOriginalClassPath}")
            }
            return this
        }

        ResilientKotlinModelAssert assertClassPathsAreEqualIfIgnoringSomeEntries(Function<String, Boolean> filter) {
            def filteredResilient = resilientModel.classPath.findAll { filter.apply(TextUtil.normaliseFileSeparators(it.absolutePath)) }
            def filteredOriginalClassPath = originalModel.classPath.findAll { filter.apply(TextUtil.normaliseFileSeparators(it.absolutePath)) }
            if (filteredResilient != filteredOriginalClassPath) {
                throw new AssertionError("Class paths are not equal after filtering some entries for script ${scriptFile}:\n" +
                    " - Riltered resilient: ${filteredResilient}\n" +
                    " - Riltered original:  ${filteredOriginalClassPath}")
            }
            return this
        }

        ResilientKotlinModelAssert assertResilientModelContainsClassPathEntriesWithPath(String path) {
            def filteredResilient = resilientModel.classPath.findAll { TextUtil.normaliseFileSeparators(it.absolutePath).contains(path) }
            if (filteredResilient.isEmpty()) {
                throw new AssertionError("Resilient Class paths for script ${scriptFile} did not contain entries with path '${path}':\n" +
                    " - Resilient classPath: ${resilientModel.classPath}")
            }
            return this
        }

        ResilientKotlinModelAssert assertImplicitImportsAreEqual() {
            if (resilientModel.implicitImports != originalModel.implicitImports) {
                throw new AssertionError("Implicit imports are not equal for script ${scriptFile}:\n" +
                    " - Resilient imports: ${resilientModel.implicitImports}\n" +
                    " - Original imports:  ${originalModel.implicitImports}")
            }
            return this
        }
    }
}
