/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class SettingsDslIntegrationSpec extends AbstractIntegrationSpec {

    def "Can access 'Settings' API in buildscript block"() {

        when:
        settingsFile << """
            buildscript {
                println startParameter.projectProperties.get('someProperty')
            }
        """

        then:
        succeeds('help')
    }

    def "Can access ExtensionAware in Groovy 'Settings'"() {
        when:
        settingsFile << """
        extensions.testValue = "aValue"
        
        assert(extensions.testValue == "aValue")
        """
        then:
        succeeds('help')
    }

    def "Can access ExtensionAware in Kotlin 'Settings'"() {
        when:
        // Need to use settings.extra because Kotlin DSL needs to be re-compiled
        settingsKotlinFile << """
        val testValue by settings.extra { "someValue" }

        assert(testValue == "someValue")
        """
        then:
        succeeds('help')
    }

    def "Can set extension value in Kotlin buildscript and access externally"() {
        when:
        // Need to use settings because Kotlin DSL needs to be re-compiled
        settingsKotlinFile << """
        buildscript {
            val aValue by settings.extra {
                "To be or not to be"
            }
        }
        
        val aValue: String by settings.extra
        
        assert(aValue == "To be or not to be")
        """
        then:
        succeeds('help')
    }

    def "Can access extension applied from external scripts"() {
        when:
        def answerFile = "answerHolder.settings.gradle.kts"

        // Need to use settings because Kotlin DSL needs to be re-compiled
        testDirectory.file(answerFile) << """
        val theAnswer: () -> Int by settings.extra {
            { 42 }
        }
        """

        settingsKotlinFile << """
        apply(from = "$answerFile")

        val theAnswer: () -> Int by settings.extra

        assert(42 == theAnswer())
        """
        then:
        succeeds('help')
    }
}
