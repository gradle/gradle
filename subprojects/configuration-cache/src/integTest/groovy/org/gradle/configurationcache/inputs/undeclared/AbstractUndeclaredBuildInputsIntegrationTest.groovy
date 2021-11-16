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

package org.gradle.configurationcache.inputs.undeclared

import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest

abstract class AbstractUndeclaredBuildInputsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    abstract void buildLogicApplication(BuildInputRead read)

    abstract String getLocation()

    def "reports undeclared system property read using #propertyRead.groovyExpression prior to task execution from plugin"() {
        buildLogicApplication(propertyRead)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRunLenient "thing", "-DCI=$value"

        then:
        configurationCache.assertStateStored()
        // TODO - use problems configurationCache, need to be able to ignore problems from the Kotlin plugin
        problems.assertResultHasProblems(result) {
            withInput("$location: system property 'CI'")
            ignoringUnexpectedInputs()
        }
        outputContains("apply = $value")
        outputContains("task = $value")

        when:
        configurationCacheRunLenient "thing", "-DCI=$value"

        then:
        configurationCache.assertStateLoaded()
        problems.assertResultHasProblems(result)
        outputDoesNotContain("apply =")
        outputContains("task = $value")

        when:
        configurationCacheRun("thing", "-DCI=$newValue")

        then: 'undeclared properties are considered build inputs'
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result)
        outputContains("apply = $newValue")
        outputContains("task = $newValue")

        where:
        propertyRead                                                                  | value  | newValue
        SystemPropertyRead.systemGetProperty("CI")                                    | "true" | "false"
        SystemPropertyRead.systemGetPropertyWithDefault("CI", "default")              | "true" | "false"
        SystemPropertyRead.systemGetPropertiesGet("CI")                               | "true" | "false"
        SystemPropertyRead.systemGetPropertiesGetProperty("CI")                       | "true" | "false"
        SystemPropertyRead.systemGetPropertiesGetPropertyWithDefault("CI", "default") | "true" | "false"
        SystemPropertyRead.integerGetInteger("CI")                                    | "12"   | "45"
        SystemPropertyRead.integerGetIntegerWithPrimitiveDefault("CI", 123)           | "12"   | "45"
        SystemPropertyRead.integerGetIntegerWithIntegerDefault("CI", 123)             | "12"   | "45"
        SystemPropertyRead.longGetLong("CI")                                          | "12"   | "45"
        SystemPropertyRead.longGetLongWithPrimitiveDefault("CI", 123)                 | "12"   | "45"
        SystemPropertyRead.longGetLongWithLongDefault("CI", 123)                      | "12"   | "45"
        SystemPropertyRead.booleanGetBoolean("CI")                                    | "true" | "false"
    }

    def "reports undeclared system property read using when iterating over system properties"() {
        buildLogicApplication(propertyRead)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("thing", "-DCI=$value")

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withInput("$location: system property 'CI'")
            ignoringUnexpectedInputs()
        }
        outputContains("apply = $value")
        outputContains("task = $value")

        where:
        propertyRead                                              | value  | newValue
        SystemPropertyRead.systemGetPropertiesFilterEntries("CI") | "true" | "false"
    }

    def "reports undeclared environment variable read using #envVarRead.groovyExpression prior to task execution from plugin"() {
        buildLogicApplication(envVarRead)
        def configurationCache = newConfigurationCacheFixture()

        when:
        EnvVariableInjection.environmentVariable("CI", value).setup(this)
        configurationCacheRunLenient "thing"

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withInput("$location: environment variable 'CI'")
            ignoringUnexpectedInputs()
        }
        outputContains("apply = $value")
        outputContains("task = $value")

        when:
        EnvVariableInjection.environmentVariable("CI", value).setup(this)
        configurationCacheRunLenient "thing"

        then:
        configurationCache.assertStateLoaded()
        problems.assertResultHasProblems(result)
        outputDoesNotContain("apply =")
        outputContains("task = $value")

        when:
        EnvVariableInjection.environmentVariable("CI", newValue).setup(this)
        configurationCacheRun("thing")

        then: 'undeclared properties are considered build inputs'
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result)
        outputContains("apply = $newValue")
        outputContains("task = $newValue")

        where:
        envVarRead                                          | value  | newValue
        EnvVariableRead.getEnv("CI")                        | "true" | "false"
        EnvVariableRead.getEnvGet("CI")                     | "true" | "false"
        EnvVariableRead.getEnvGetOrDefault("CI", "default") | "true" | "false"
    }
}
