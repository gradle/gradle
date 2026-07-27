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

    def "can dynamically access properties"() {

        given:
        propertiesFile << "someProjectProperty=true"
        settingsFile << """
            if (getProperty('someProjectProperty') == 'true') {
                println('getProperty worked')
            }
            if (someProjectProperty == 'true') {
                println('dynamic property access worked')
            }
        """

        when:
        succeeds('help')

        then:
        outputContains('etProperty worked')
        outputContains('dynamic property access worked')
    }

    def "Can type-safely use ExtensionAware with the Groovy DSL"() {
        when:
        def answerFile = "answerHolder.gradle"
        testDirectory.file(answerFile) << """
        extensions["theAnswer"] = {
            42
        }
        """

        settingsFile << """
        buildscript {
            extensions["aValue"] = "hello"

            assert extensions["aValue"] == "hello" : "Can access inside buildscript"
        }

        assert extensions["aValue"] == "hello" : "Can access outside buildscript"

        apply from: '$answerFile'

        assert(extensions["theAnswer"]() == 42) : "Can access from applied file"
        """
        then:
        succeeds('help')
    }

    def "Can type-safely use ExtensionAware with the Kotlin DSL"() {
        when:
        // Need to use settings.extra because Kotlin DSL needs to be re-compiled
        def answerFile = "answerHolder.settings.gradle.kts"

        // Need to use settings because Kotlin DSL needs to be re-compiled
        testDirectory.file(answerFile) << """
        settings.extra["theAnswer"] = { 42 }
        """

        settingsKotlinFile << """
        buildscript {
            settings.extra["aValue"] = "hello"

            assert(settings.extra["aValue"] == "hello") {
                "Can access inside buildscript"
            }

            settings.extra["hamlet"] = "To be or not to be"

            assert(settings.extra["hamlet"] == "To be or not to be") {
                "Can access inside buildscript"
            }
        }

        assert(settings.extra["aValue"] == "hello") {
            "Can access outside buildscript"
        }

        val hamlet = settings.extra["hamlet"] as String

        assert(hamlet == "To be or not to be") {
            "Can access outside buildscript"
        }

        apply(from = "$answerFile")

        @Suppress("UNCHECKED_CAST")
        val theAnswer = settings.extra["theAnswer"] as () -> Int

        assert(theAnswer() == 42) {
            "Can access from applied file"
        }
        """
        then:
        succeeds('help')
    }

    def 'settings script classpath has proper usage attribute'() {
        settingsFile << """
buildscript {
    configurations.classpath {
        def value = attributes.getAttribute(Usage.USAGE_ATTRIBUTE)
        assert value.name == Usage.JAVA_RUNTIME
    }
}
"""
        expect:
        succeeds()
    }

}
