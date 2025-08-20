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
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.tooling.model.kotlin.dsl.ResilientKotlinDslScriptsModel
import org.gradle.util.internal.ToBeImplemented

import java.util.function.Function

@ToolingApiVersion('>=9.2')
@TargetGradleVersion('>=9.2')
class ResilientKotlinDslScriptsModelBuilderCrossVersionSpec extends ToolingApiSpecification {

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
            action(new OriginalModelAction()).run()
        }

        then:
        original

        when:
        def resilientModels = succeeds {
            action(new ResilientModelAction())
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
    }

    def "returns all successful and first failed script model when #description"() {
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
            action(new OriginalModelAction()).run()
        }

        then:
        original

        when:
        c << """$breakage"""
        def resilientModels = succeeds {
            action(new ResilientModelAction())
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

        where:
        description                | breakage                                     | expectedFailure
        "scripts evaluation fails" | "throw RuntimeException(\"Failing script\")" | "Failing script"
        "script compilation fails" | "broken !!!"                                 | "broken !!!"
    }

    def "returns scripts models when project convention plugin is failing with exception"() {
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
            action(new OriginalModelAction()).run()
        }

        then:
        original

        when:
        projectPlugin << "throw RuntimeException(\"Failing script\")"
        def resilientModels = succeeds {
            action(new ResilientModelAction())
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
    }

    def "returns scripts models when project convention plugin is failing with compile error"() {
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
            action(new OriginalModelAction()).run()
        }

        then:
        original

        when:
        projectPlugin << """ broken !!! """
        def resilientModels = succeeds {
            action(new ResilientModelAction()).withArguments("-Dorg.gradle.internal.resilient-model-building=true").run()
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
        resilientModels.failureMessages.size() == 2
        resilientModels.failureMessages[settingsKotlinFile.parentFile].contains("A problem occurred configuring project ':b'.")
        resilientModels.failureMessages[included].contains("Execution failed for task ':build-logic:compileKotlin'.")
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
            action(new OriginalModelAction()).run()
        }

        then:
        original

        when:
        settingsPlugin << """ broken !!! """
        fails {
            action(new ResilientModelAction()).withArguments("-Dorg.gradle.internal.resilient-model-building=true").run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.message.startsWith("The supplied build action failed with an exception.")
    }

    static class MyCustomModel implements Serializable {

        final Map<File, KotlinDslScriptModel> scriptModels
        final Map<File, String> failureMessages

        MyCustomModel(Map<File, KotlinDslScriptModel> scriptModels, Map<File, Failure> failure) {
            this.scriptModels = scriptModels
            this.failureMessages = failure.collectEntries { k, v -> [(k): TextUtil.normaliseFileSeparators(v.description)] }
        }
    }

    static class OriginalModelAction implements BuildAction<MyCustomModel>, Serializable {

        @Override
        MyCustomModel execute(BuildController controller) {
            GradleBuild gradleBuild = controller.getModel(GradleBuild.class)
            Map<File, KotlinDslScriptModel> scriptModels = [:]
            KotlinDslScriptsModel buildScriptModel = controller.getModel(gradleBuild.rootProject, KotlinDslScriptsModel.class)
            scriptModels += buildScriptModel.scriptModels
            for (GradleBuild build : gradleBuild.includedBuilds) {
                buildScriptModel = controller.getModel(build.rootProject, KotlinDslScriptsModel.class)
                scriptModels += buildScriptModel.scriptModels
            }

            return new MyCustomModel(
                scriptModels,
                [:]
            )
        }
    }

    static class ResilientModelAction implements BuildAction<MyCustomModel>, Serializable {

        @Override
        MyCustomModel execute(BuildController controller) {
            GradleBuild gradleBuild = controller.getModel(GradleBuild.class)
            Map<File, KotlinDslScriptModel> scriptModels = [:]
            Map<File, Failure> failures = [:]
            ResilientKotlinDslScriptsModel buildScriptModel = controller.getModel(gradleBuild.rootProject, ResilientKotlinDslScriptsModel.class)
            scriptModels += buildScriptModel.model.scriptModels
            if (buildScriptModel.failure) {
                failures[gradleBuild.buildIdentifier.rootDir] = buildScriptModel.failure
            }
            for (GradleBuild build : gradleBuild.includedBuilds) {
                def root = build.rootProject
                buildScriptModel = controller.getModel(root, ResilientKotlinDslScriptsModel.class)
                scriptModels += buildScriptModel.model.scriptModels
                if (buildScriptModel.failure) {
                    failures[build.buildIdentifier.rootDir] = buildScriptModel.failure
                }
            }

            return new MyCustomModel(
                scriptModels,
                failures
            )
        }
    }

    static class ResilientKotlinModelAssert {

        MyCustomModel resilientCustomModel
        MyCustomModel originalCustomModel
        KotlinDslScriptModel resilientModel
        KotlinDslScriptModel originalModel
        File scriptFile

        ResilientKotlinModelAssert(File scriptFile, MyCustomModel resilientModel, MyCustomModel originalModel) {
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
