/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.kotlin.dsl.tooling.builders.KotlinScriptModelParameters.setModelParameters

@TargetGradleVersion(">=6.8")
@Flaky(because = 'https://github.com/gradle/gradle-private/issues/3414')
@LeaksFileHandles("Kotlin Compiler Daemon taking time to shut down")
class KotlinDslDefaultScriptsModelCrossVersionSpec extends AbstractKotlinDslScriptsModelCrossVersionSpec {

    def "can fetch model for the init scripts of a build"() {

        given:
        def spec = withBuildSrcAndInitScripts()

        when:
        def model = loadValidatedToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, false)
        }

        then:
        model.scriptModels.keySet() == spec.scripts.values() as Set

        and:
        assertModelMatchesBuildSpec(model, spec)
    }

    def "can fetch model for the init scripts of a build in lenient mode"() {

        given:
        def spec = withBuildSrcAndInitScripts()

        and:
        spec.scripts.init << """
            script_body_compilation_error
        """

        when:
        def model = loadValidatedToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, true)
        }

        then:
        model.scriptModels.keySet() == spec.scripts.values() as Set

        and:
        assertModelMatchesBuildSpec(model, spec)

        and:
        assertHasExceptionMessage(
            model,
            spec.scripts.init,
            "Unresolved reference: script_body_compilation_error"
        )
    }
}
