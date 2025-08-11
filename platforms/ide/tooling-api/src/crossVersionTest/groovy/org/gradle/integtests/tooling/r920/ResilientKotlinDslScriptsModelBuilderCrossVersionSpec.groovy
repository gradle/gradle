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

package org.gradle.integtests.tooling.r920;

import org.gradle.integtests.tooling.fixture.TargetGradleVersion;
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification;
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel;

@ToolingApiVersion('>=9.2')
@TargetGradleVersion('>=9.2')
class ResilientKotlinDslScriptsModelBuilderCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile.delete()
    }

    def "basic project w/ included build - broken included build settings file and build script - strict mode - build action"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = file("included")
        def includedSettings = included.file("settings.gradle.kts") << """

        """
        included.file("build.gradle.kts") << """
            plugins {
                id("java")
            }
            boom !!!
        """

        when:
        def model = succeeds {
            action(new CustomModelAction()).run()
        }

        then:
        model.scriptModels.size() == 3
    }

    static class MyCustomModel implements Serializable {

        Map<File, KotlinDslScriptModel> scriptModels

        MyCustomModel(Map<File, KotlinDslScriptModel> scriptModels) {
            this.scriptModels = scriptModels
        }

    }

    static class CustomModelAction implements BuildAction<MyCustomModel>, Serializable {

        @Override
        MyCustomModel execute(BuildController controller) {
            KotlinDslScriptsModel buildScriptModel = controller.getModel(KotlinDslScriptsModel.class)
            KotlinDslScriptsModel buildScriptModel2 = controller.getModel(KotlinDslScriptsModel.class)

            return new MyCustomModel(
                buildScriptModel.scriptModels,
            )
        }

    }

}
