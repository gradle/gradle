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

package org.gradle.configurationcache

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.configurationcache.fixtures.BuildLogicChangeFixture

import static org.junit.Assume.assumeFalse

class ConfigurationCacheIncludedBuildInputsChangesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "invalidates cache upon change to #inputName used by included build"() {

        assumeFalse(
            'property from gradle.properties is not available to included build',
            inputName == 'gradle.properties'
        )

        given:
        def configurationCache = newConfigurationCacheFixture()
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
                parameters.value.set(providers.systemProperty("test_is_ci"))
            }

            val isCi = ${inputExpression}
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
            pluginManagement {
                includeBuild 'build-logic'
            }
        """
        buildFile << """
            plugins { id('$fixture.pluginId') }
        """

        when:
        configurationCacheRunLenient fixture.task

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRunLenient fixture.task

        then: "included build doesn't build"
        output.count("CI") == 0
        configurationCache.assertStateLoaded()

        when:
        if (inputName == 'gradle.properties') {
            file('gradle.properties').text = 'test_is_ci=true'
            configurationCacheRunLenient fixture.task
        } else {
            configurationCacheRunLenient fixture.task, inputArgument
        }

        then:
        output.count("ON CI") == 1
        configurationCache.assertStateStored()

        where:
        inputName             | inputExpression                          | inputArgument
        'custom value source' | 'ciProvider'                             | '-Dtest_is_ci=true'
        'system property'     | 'providers.systemProperty("test_is_ci")' | '-Dtest_is_ci=true'
        'Gradle property'     | 'providers.gradleProperty("test_is_ci")' | '-Ptest_is_ci=true'
        'gradle.properties'   | 'providers.gradleProperty("test_is_ci")' | ''
    }
}
