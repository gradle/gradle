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

package org.gradle.kotlin.dsl.tooling.builders.r60

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.util.GradleVersion

import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinScriptModelParameters.setModelParameters

@TargetGradleVersion(">=6.0")
@LeaksFileHandles("Kotlin Compiler Daemon taking time to shut down")
@Flaky(because = 'https://github.com/gradle/gradle-private/issues/3414')
class KotlinDslDefaultScriptsModelCrossVersionSpec extends AbstractKotlinDslScriptsModelCrossVersionSpec {


    def "can fetch model for the scripts of a build"() {

        given:
        def spec = withMultiProjectBuildWithBuildSrc()

        when:
        maybeExpectAccessorsDeprecation()
        def model = loadToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, false)
        }

        then:
        model.scriptModels.keySet() == spec.scripts.values() as Set

        and:
        assertModelMatchesBuildSpec(model, spec)
    }

    def "can fetch model for the scripts of a build in lenient mode"() {

        given:
        def spec = withMultiProjectBuildWithBuildSrc()

        and:
        spec.scripts.a << """
            script_body_compilation_error
        """

        when:
        maybeExpectAccessorsDeprecation()
        withStackTraceChecksDisabled() // This test prints a huge stack trace
        def model = loadToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, true)
        }

        then:
        model.scriptModels.keySet() == spec.scripts.values() as Set

        and:
        assertModelMatchesBuildSpec(model, spec)

        and:
        assertHasExceptionMessage(
            model,
            spec.scripts.a,
            "Unresolved reference: script_body_compilation_error"
        )
    }

    void maybeExpectAccessorsDeprecation() {
        if (targetVersion >= GradleVersion.version("7.6") && targetVersion < GradleVersion.version("8.0")) {
            expectDocumentedDeprecationWarning("Non-strict accessors generation for Kotlin DSL precompiled script plugins has been deprecated. This will change in Gradle 9.0. Strict accessor generation will become the default. To opt in to the strict behavior, set the 'org.gradle.kotlin.dsl.precompiled.accessors.strict' system property to `true`. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#strict-kotlin-dsl-precompiled-scripts-accessors")
        }
    }
}
