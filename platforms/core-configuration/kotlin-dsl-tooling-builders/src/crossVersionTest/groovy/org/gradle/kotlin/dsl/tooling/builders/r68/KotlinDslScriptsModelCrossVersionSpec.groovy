/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders.r68

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.kotlin.dsl.tooling.builders.KotlinScriptModelParameters.setModelParameters

@TargetGradleVersion(">=6.8")
@LeaksFileHandles("Kotlin Compiler Daemon taking time to shut down")
class KotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinDslScriptsModelCrossVersionSpec {

    def "single request models for init scripts equal multi requests models"() {

        given:
        def spec = withBuildSrcAndInitScripts()


        when:
        def model = loadValidatedToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, false, true, [])
        }
        Map<File, KotlinDslScriptModel> singleRequestModels = model.scriptModels

        and:
        Map<File, KotlinBuildScriptModel> multiRequestsModels = spec.scripts.values().collectEntries {
            [(it): kotlinBuildScriptModelFor(projectDir, it)]
        }

        then:
        spec.scripts.values().each { script ->
            assert singleRequestModels[script].classPath == multiRequestsModels[script].classPath
            assert singleRequestModels[script].sourcePath == multiRequestsModels[script].sourcePath
            assert singleRequestModels[script].implicitImports == multiRequestsModels[script].implicitImports
        }
    }
}
