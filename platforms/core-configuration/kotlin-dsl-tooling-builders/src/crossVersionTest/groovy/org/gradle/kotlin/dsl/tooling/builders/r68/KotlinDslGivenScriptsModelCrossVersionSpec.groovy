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
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinScriptModelParameters.setModelParameters

@TargetGradleVersion(">=6.8")
@LeaksFileHandles("Kotlin Compiler Daemon taking time to shut down")
class KotlinDslGivenScriptsModelCrossVersionSpec extends AbstractKotlinDslScriptsModelCrossVersionSpec {

    def "can fetch model for a given set of init scripts"() {

        given:
        def spec = withBuildSrcAndInitScripts()
        def requestedScripts = spec.scripts.values()

        when:
        def model = loadToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, false, true, requestedScripts)
        }

        then:
        model.scriptModels.keySet() == requestedScripts as Set

        and:
        assertModelMatchesBuildSpec(model, spec)
    }

    def "can fetch model for a given set of init scripts of a build in lenient mode"() {

        given:
        def spec = withBuildSrcAndInitScripts()
        def requestedScripts = spec.scripts.values()

        and:
        spec.scripts.init << """
            script_body_compilation_error
        """

        when:
        def model = loadToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, true, true, [buildFileKts])
        }

        then:
        model.scriptModels.keySet() == requestedScripts as Set

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
