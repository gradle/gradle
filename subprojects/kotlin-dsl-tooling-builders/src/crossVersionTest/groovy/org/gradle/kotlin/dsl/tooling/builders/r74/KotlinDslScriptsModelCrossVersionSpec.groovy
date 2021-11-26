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

package org.gradle.kotlin.dsl.tooling.builders.r74

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import spock.lang.Issue

@ToolingApiVersion(">=6.0")
@TargetGradleVersion(">=7.4")
@Issue("https://github.com/gradle/gradle/issues/18637")
class KotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "model query captures exceptions from #exception in lenient mode"() {
        given:
        withBuildScript(exceptionBlock)
        withDefaultSettings()

        when:
        def scriptsModel = kotlinDslScriptsModelFor(true, false, [])
        def buildScriptModel = scriptsModel.scriptModels.find { it.key.name == 'build.gradle.kts' }.value

        then:
        buildScriptModel.exceptions.size() == 1
        buildScriptModel.exceptions[0].contains(message)

        where:
        exception                | message                         | exceptionBlock
        'afterEvaluate(Action)'  | 'faulty afterEvaluate(Action)'  | 'afterEvaluate { throw RuntimeException("faulty afterEvaluate(Action)") }'
        'afterEvaluate(Closure)' | 'faulty afterEvaluate(Closure)' | 'afterEvaluate(closureOf<Project> { throw RuntimeException("faulty afterEvaluate(Closure)") })'
    }
}
