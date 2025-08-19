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
            assert resilientScripts.contains(scriptFile)
            def originalModel = original.scriptModels.get(scriptFile)
            def resilientModel = resilientModels.scriptModels.get(scriptFile)
            assert resilientModel.classPath == originalModel.classPath
            assert resilientModel.implicitImports == originalModel.implicitImports
        }
        resilientModels.failureMessage == null
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
            if (scriptFile != d) {
                assert resilientScripts.contains(scriptFile)
                def originalModel = original.scriptModels.get(scriptFile)
                def resilientModel = resilientModels.scriptModels.get(scriptFile)
                assert resilientModel.classPath == originalModel.classPath
                assert resilientModel.implicitImports == originalModel.implicitImports
            } else {
                def originalModel = original.scriptModels.get(scriptFile)
                def resilientModel = resilientModels.scriptModels.get(scriptFile)
                assert resilientModel.classPath == originalModel.classPath.findAll { !it.absolutePath.contains("/accessors/") }
                assert resilientModel.implicitImports == originalModel.implicitImports
            }
        }
        resilientModels.failureMessage.contains("c/build.gradle.kts' line: 5")
        resilientModels.failureMessage.contains(expectedFailure)

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
            if (scriptFile == b) {
                assert resilientScripts.contains(scriptFile)
                def originalModel = original.scriptModels.get(scriptFile)
                def resilientModel = new Tuple2<>(scriptFile, resilientModels.scriptModels.get(scriptFile))
                // For some reason the build logic and accessors are included but different
                assert resilientModel.v2.classPath.findAll { !it.absolutePath.contains("/build-logic.jar") }.findAll { !it.absolutePath.contains("/accessors/") } == originalModel.classPath.findAll { !it.absolutePath.contains("/accessors/") }.findAll { !it.absolutePath.contains("/build-logic.jar") }
                assert resilientModel.v2.classPath.find { it.absolutePath.contains("/accessors/") }
                assert resilientModel.v2.classPath.find { it.absolutePath.contains("/build-logic.jar") }
                assert resilientModel.v2.implicitImports == originalModel.implicitImports
            } else if (scriptFile == c) {
                assert resilientScripts.contains(scriptFile)
                def originalModel = original.scriptModels.get(scriptFile)
                def resilientModel = new Tuple2<>(scriptFile, resilientModels.scriptModels.get(scriptFile))
                assert resilientModel.v2.classPath == originalModel.classPath.findAll { !it.absolutePath.contains("/accessors/") }
                assert resilientModel.v2.implicitImports == originalModel.implicitImports
            } else {
                assert resilientScripts.contains(scriptFile)
                def originalModel = original.scriptModels.get(scriptFile)
                def resilientModel = new Tuple2<>(scriptFile, resilientModels.scriptModels.get(scriptFile))
                assert resilientModel.v2.classPath == originalModel.classPath
                assert resilientModel.v2.implicitImports == originalModel.implicitImports
            }
        }
        resilientModels.failureMessage.contains("b/build.gradle.kts' line: 2")
        resilientModels.failureMessage.contains("Failing script")
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
            if (scriptFile == b) {
                assert resilientScripts.contains(scriptFile)
                def originalModel = original.scriptModels.get(scriptFile)
                def resilientModel = new Tuple2<>(scriptFile, resilientModels.scriptModels.get(scriptFile))
                // In this case we don't have accessors and build-logic in the classpath
                assert resilientModel.v2.classPath == originalModel.classPath.findAll { !it.absolutePath.contains("/accessors/") }.findAll { !it.absolutePath.contains("/build-logic.jar") }
                assert resilientModel.v2.implicitImports == originalModel.implicitImports
            } else if (scriptFile == c) {
                assert resilientScripts.contains(scriptFile)
                def originalModel = original.scriptModels.get(scriptFile)
                def resilientModel = new Tuple2<>(scriptFile, resilientModels.scriptModels.get(scriptFile))
                // In this case we don't have accessors, since this project is not configured
                assert resilientModel.v2.classPath == originalModel.classPath.findAll { !it.absolutePath.contains("/accessors/") }
                assert resilientModel.v2.implicitImports == originalModel.implicitImports
            } else {
                assert resilientScripts.contains(scriptFile)
                def originalModel = original.scriptModels.get(scriptFile)
                def resilientModel = new Tuple2<>(scriptFile, resilientModels.scriptModels.get(scriptFile))
                assert resilientModel.v2.classPath == originalModel.classPath
                assert resilientModel.v2.implicitImports == originalModel.implicitImports
            }
        }
        resilientModels.failureMessage.contains("Execution failed for task ':build-logic:compileKotlin'")
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

        Map<File, KotlinDslScriptModel> scriptModels

        String failureMessage

        MyCustomModel(Map<File, KotlinDslScriptModel> scriptModels, Failure failure) {
            this.scriptModels = scriptModels
            this.failureMessage = failure ? failure.description : null
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
                null
            )
        }
    }

    static class ResilientModelAction implements BuildAction<MyCustomModel>, Serializable {

        @Override
        MyCustomModel execute(BuildController controller) {
            GradleBuild gradleBuild = controller.getModel(GradleBuild.class)
            Map<File, KotlinDslScriptModel> scriptModels = [:]
            Failure failure = null
            ResilientKotlinDslScriptsModel buildScriptModel = controller.getModel(gradleBuild.rootProject, ResilientKotlinDslScriptsModel.class)
            scriptModels += buildScriptModel.model.scriptModels
            if (buildScriptModel.failure) {
                failure = buildScriptModel.failure
            }
            for (GradleBuild build : gradleBuild.includedBuilds) {
                def root = build.rootProject
                buildScriptModel = controller.getModel(root, ResilientKotlinDslScriptsModel.class)
                scriptModels += buildScriptModel.model.scriptModels
                if (buildScriptModel.failure) {
                    failure = buildScriptModel.failure
                }
            }

            return new MyCustomModel(
                scriptModels,
                failure
            )
        }
    }
}
