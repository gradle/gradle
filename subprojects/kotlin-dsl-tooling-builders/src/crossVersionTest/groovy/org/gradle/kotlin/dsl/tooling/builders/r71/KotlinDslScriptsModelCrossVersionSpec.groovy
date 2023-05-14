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

package org.gradle.kotlin.dsl.tooling.builders.r71

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.test.fixtures.Flaky
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.kotlin.dsl.tooling.builders.KotlinScriptModelParameters.setModelParameters

@TargetGradleVersion(">=7.1")
class KotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    @Flaky(because = "https://github.com/gradle/gradle-private/issues/3708")
    def "kotlin-dsl project with pre-compiled script plugins should import without errors"() {

        given:
        withBuildScript """
            plugins { `kotlin-dsl` }
            $repositoriesBlock
        """
        file("src/main/kotlin/myplugin.gradle.kts") << ''

        when:
        def model = loadValidatedToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, false)
        }

        then:
        !model.scriptModels.isEmpty()
        model.scriptModels.each {
            assert it.value.exceptions.isEmpty() : it.value.exceptions
        }
    }
}
