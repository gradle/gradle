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

package org.gradle.internal.cc.impl

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.internal.cc.impl.fixtures.BuildLogicChangeFixture
import org.gradle.internal.cc.impl.inputs.undeclared.EnvVariableInjection
import org.gradle.util.internal.ToBeImplemented

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

    def "invalidates cache upon change to environment variable read by included build logic task"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        withIncludedBuildLogicTaskReadingEnvVar()

        when:
        executer.withEnvironmentVars(FOO: "one")
        configurationCacheRun "greet"

        then:
        configurationCache.assertStateStored()
        outputContains("FOO = one")

        when:
        executer.withEnvironmentVars(FOO: "two")
        configurationCacheRun "greet"

        then:
        configurationCache.assertStateStored()
        outputContains("environment variable 'FOO' has changed")
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/39887")
    def "invalidates cache upon change to environment variable read by included build logic task that was up-to-date when cache entry was stored"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        withIncludedBuildLogicTaskReadingEnvVar()

        when: "build logic is built without configuration cache"
        executer.withEnvironmentVars(FOO: "one")
        run "greet", "--no-configuration-cache"

        then:
        outputContains("FOO = one")

        when: "build logic task is up-to-date, so its environment variable read is not recorded"
        executer.withEnvironmentVars(FOO: "one")
        configurationCacheRun "greet"

        then:
        configurationCache.assertStateStored()
        result.assertTaskSkipped(":build-logic:jar")
        outputDoesNotContain("FOO =")

        when:
        executer.withEnvironmentVars(FOO: "two")
        configurationCacheRun "greet"

        then:
        // TODO: the cache entry should be invalidated, because the build logic task reads FOO
        configurationCache.assertStateLoaded()
        outputDoesNotContain("environment variable 'FOO' has changed")
    }

    private void withIncludedBuildLogicTaskReadingEnvVar() {
        EnvVariableInjection.checkEnvironmentVariableUnset("FOO")
        settingsFile """
            pluginManagement {
                includeBuild("build-logic")
            }
        """
        buildFile """
            plugins { id("my-plugin") }
        """
        settingsFile "build-logic/settings.gradle", ""
        buildFile "build-logic/build.gradle", """
            plugins { id("groovy-gradle-plugin") }

            tasks.named("jar") {
                doLast {
                    // not declared as an input
                    println("FOO = " + System.getenv("FOO"))
                }
            }
        """
        groovyFile "build-logic/src/main/groovy/my-plugin.gradle", """
            tasks.register("greet") {
                doLast { println("Hello!") }
            }
        """
    }
}
