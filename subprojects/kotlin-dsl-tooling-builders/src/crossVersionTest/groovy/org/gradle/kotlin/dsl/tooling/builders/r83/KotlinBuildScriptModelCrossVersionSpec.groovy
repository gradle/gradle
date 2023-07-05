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

class KotlinBuildScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    @TargetGradleVersion(">=8.3")
    @Issue("https://github.com/gradle/gradle/issues/25555")
    def "single project with parallel build should not emit configuration resolution deprecation warning"() {
        given:
        propertiesFile << """
            org.gradle.warning.mode=all
            org.gradle.parallel=true
            """

        when:
        def model = loadValidatedToolingModel(KotlinDslScriptsModel)

        then:
        if (shouldCheckForDeprecationWarnings()) {
            assert !stdout.toString().contains("Resolution of the configuration")
        }
    }

    @TargetGradleVersion(">=8.3")
    @Issue("https://github.com/gradle/gradle/issues/25555")
    def "multi project with parallel build should not emit configuration resolution deprecation warning"() {
            given:
            withSingleSubproject()

            propertiesFile << """
            # JVM arguments for connecting to debugger
            #org.gradle.debug=true
            #org.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=localhost:4455
            org.gradle.warning.mode=all
            org.gradle.parallel=true
            # Show stacktrace
            org.gradle.logging.stacktrace=full
            """

            when:
            def model = loadValidatedToolingModel(KotlinDslScriptsModel)

            then:
            if (shouldCheckForDeprecationWarnings()) {
                assert !stdout.toString().contains("Resolution of the configuration")
            }
    }

}
