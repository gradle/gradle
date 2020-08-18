/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.instantexecution.fixtures.BuildLogicChangeFixture
import org.gradle.instantexecution.fixtures.ScriptChangeFixture
import org.junit.Test
import spock.lang.Unroll

import static org.junit.Assume.assumeFalse

class ConfigurationCacheIncludedBuildChangesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Unroll
    @Test
    def "invalidates cache upon change to #scriptChangeSpec of included build"() {
        given:
        def instant = newInstantExecutionFixture()
        def fixture = scriptChangeSpec.fixtureForProjectDir(file('build-logic'))
        fixture.setup()
        def build = { instantRunLenient(*fixture.buildArguments) }
        settingsFile << """
            includeBuild 'build-logic'
        """

        when:
        build()

        then:
        outputContains fixture.expectedOutputBeforeChange

        when:
        fixture.applyChange()
        build()

        then:
        outputContains fixture.expectedOutputAfterChange
        instant.assertStateStored()

        when:
        build()

        then:
        outputDoesNotContain fixture.expectedOutputBeforeChange
        outputDoesNotContain fixture.expectedOutputAfterChange
        instant.assertStateLoaded()

        where:
        scriptChangeSpec << ScriptChangeFixture.specs()
    }

    @Unroll
    def "invalidates cache upon change to included #fixtureSpec"() {
        given:
        def instant = newInstantExecutionFixture()
        def fixture = fixtureSpec.fixtureForProjectDir(file('build-logic'))
        fixture.setup()
        settingsFile << """
            includeBuild 'build-logic'
        """
        buildFile << """
            plugins { id('$fixture.pluginId') }
        """

        when:
        instantRunLenient fixture.task

        then:
        outputContains fixture.expectedOutputBeforeChange
        instant.assertStateStored()

        when:
        fixture.applyChange()
        instantRunLenient fixture.task

        then:
        outputContains fixture.expectedOutputAfterChange
        instant.assertStateStored()

        when:
        instantRunLenient fixture.task

        then:
        outputContains fixture.expectedOutputAfterChange
        instant.assertStateLoaded()

        where:
        fixtureSpec << BuildLogicChangeFixture.specs()
    }

    @Unroll
    def "invalidates cache upon change to #inputName used by included build"() {

        assumeFalse(
            'property from gradle.properties is not available to included build',
            inputName == 'gradle.properties'
        )

        given:
        def instant = newInstantExecutionFixture()
        def fixture = new BuildLogicChangeFixture(file('build-logic'))
        fixture.setup()
        fixture.buildFile << """

            interface Params : $ValueSourceParameters.name {
                val value: Property<String>
            }

            abstract class IsCi : $ValueSource.name<String, Params> {
                override fun obtain(): String? = parameters.value.orNull
            }

            val ciProvider = providers.of(IsCi::class.java) {
                parameters.value.set(providers.systemProperty("test_is_ci").forUseAtConfigurationTime())
            }

            val isCi = ${inputExpression}.forUseAtConfigurationTime()
            tasks {
                named("jar") {
                    if (isCi.isPresent) {
                        doLast { println("ON CI") }
                    } else {
                        doLast { println("NOT CI") }
                    }
                }
            }
        """
        settingsFile << """
            includeBuild 'build-logic'
        """
        buildFile << """
            plugins { id('$fixture.pluginId') }
        """

        when:
        instantRunLenient fixture.task

        then:
        output.count("NOT CI") == 1
        instant.assertStateStored()

        when:
        instantRunLenient fixture.task

        then: "included build doesn't build"
        output.count("CI") == 0
        instant.assertStateLoaded()

        when:
        if (inputName == 'gradle.properties') {
            file('gradle.properties').text = 'test_is_ci=true'
            instantRunLenient fixture.task
        } else {
            instantRunLenient fixture.task, inputArgument
        }

        then:
        output.count("ON CI") == 1
        instant.assertStateStored()

        where:
        inputName             | inputExpression                          | inputArgument
        'custom value source' | 'ciProvider'                             | '-Dtest_is_ci=true'
        'system property'     | 'providers.systemProperty("test_is_ci")' | '-Dtest_is_ci=true'
        'Gradle property'     | 'providers.gradleProperty("test_is_ci")' | '-Ptest_is_ci=true'
        'gradle.properties'   | 'providers.gradleProperty("test_is_ci")' | ''
    }
}
