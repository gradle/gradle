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

class ConfigurationCacheBuildSrcChangesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "invalidates cache upon change to buildSrc #changeFixtureSpec"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def changeFixture = changeFixtureSpec.fixtureForProjectDir(file('buildSrc'))
        changeFixture.setup()
        buildFile << """
            plugins { id('$changeFixture.pluginId') }
        """

        when:
        configurationCacheRun changeFixture.task

        then:
        outputContains changeFixture.expectedOutputBeforeChange
        configurationCache.assertStateStored()

        when:
        changeFixture.applyChange()
        configurationCacheRun changeFixture.task

        then:
        outputContains changeFixture.expectedCacheInvalidationMessage
        outputContains changeFixture.expectedOutputAfterChange
        configurationCache.assertStateStored()

        when:
        configurationCacheRun changeFixture.task

        then:
        outputContains changeFixture.expectedOutputAfterChange
        configurationCache.assertStateLoaded()

        where:
        changeFixtureSpec << BuildLogicChangeFixture.specs()
    }

    def "invalidates cache upon change to #inputName used by buildSrc"() {

        assumeFalse(
            'property from gradle.properties is not available to buildSrc',
            inputName == 'gradle.properties'
        )

        given:
        def configurationCache = newConfigurationCacheFixture()
        file("buildSrc/build.gradle.kts").text = """

            interface Params: $ValueSourceParameters.name {
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
                if (isCi.isPresent) {
                    register("run") {
                        doLast { println("ON CI") }
                    }
                } else {
                    register("run") {
                        doLast { println("NOT CI") }
                    }
                }
                jar {
                    dependsOn("run")
                }
            }
        """
        buildFile << """
            task assemble
        """

        when:
        configurationCacheRun "assemble"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "assemble"

        then: "buildSrc doesn't build"
        output.count("CI") == 0
        configurationCache.assertStateLoaded()

        when:
        if (inputName == 'gradle.properties') {
            file('gradle.properties').text = 'test_is_ci=true'
            configurationCacheRun "assemble"
        } else {
            configurationCacheRun "assemble", inputArgument
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
