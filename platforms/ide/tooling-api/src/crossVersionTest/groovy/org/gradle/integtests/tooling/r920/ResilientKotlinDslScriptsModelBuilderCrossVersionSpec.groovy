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
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.tooling.model.kotlin.dsl.ResilientKotlinDslScriptsModel

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
            KotlinDslScriptsModel buildScriptModel = controller.getModel(KotlinDslScriptsModel.class)

            return new MyCustomModel(
                buildScriptModel.scriptModels,
                null
            )
        }
    }

    static class ResilientModelAction implements BuildAction<MyCustomModel>, Serializable {

        @Override
        MyCustomModel execute(BuildController controller) {
            GradleBuild gradleBuild = controller.getModel(GradleBuild.class)
            ResilientKotlinDslScriptsModel buildScriptModel = controller.getModel(gradleBuild.rootProject, ResilientKotlinDslScriptsModel.class)

            return new MyCustomModel(
                buildScriptModel.model.scriptModels,
                buildScriptModel.failure
            )
        }
    }

}
