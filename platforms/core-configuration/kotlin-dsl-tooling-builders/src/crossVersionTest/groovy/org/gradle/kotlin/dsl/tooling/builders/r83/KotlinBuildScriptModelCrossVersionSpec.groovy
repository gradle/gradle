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

package org.gradle.kotlin.dsl.tooling.builders.r83

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import spock.lang.Issue

@TargetGradleVersion(">=8.3")
class KotlinBuildScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    @Issue("https://github.com/gradle/gradle/issues/25555")
    def "single project with parallel build should not emit configuration resolution deprecation warning"() {
        given:
        propertiesFile << gradleProperties

        expect:
        loadValidatedToolingModel(KotlinDslScriptsModel)
    }

    @Issue("https://github.com/gradle/gradle/issues/25555")
    def "multi project with parallel build should not emit configuration resolution deprecation warning"() {
        given:
        withSingleSubproject()
        propertiesFile << gradleProperties

        expect:
        loadValidatedToolingModel(KotlinDslScriptsModel)
    }

    private String getGradleProperties() {
        """
        org.gradle.warning.mode=all
        org.gradle.parallel=true
        """
    }
}
