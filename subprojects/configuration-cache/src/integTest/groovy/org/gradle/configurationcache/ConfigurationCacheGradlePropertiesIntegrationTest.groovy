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

package org.gradle.configurationcache

import groovy.test.NotYetImplemented
import org.gradle.configurationcache.fixtures.BuildLogicChangeFixture
import spock.lang.Issue

import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

class ConfigurationCacheGradlePropertiesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "invalidates cache when set of Gradle property defining system properties changes"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            println(gradleProp + '!')
        """

        when:
        configurationCacheRun "help", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}gradleProp=1"

        then:
        outputContains '1!'
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}gradleProp=1"

        then:
        outputDoesNotContain '1!'
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "help", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}gradleProp=2"

        then:
        outputContains '2!'
        outputContains "because the set of system properties prefixed by '${SYSTEM_PROJECT_PROPERTIES_PREFIX}' has changed."
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}gradleProp=2", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}unusedProp=2"

        then:
        outputContains '2!'
        outputContains "because the set of system properties prefixed by '${SYSTEM_PROJECT_PROPERTIES_PREFIX}' has changed."
        configurationCache.assertStateStored()
    }

    def "invalidates cache when set of Gradle property defining environment variables changes"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            println(gradleProp + '!')
        """

        when:
        executer.withEnvironmentVars([
            (ENV_PROJECT_PROPERTIES_PREFIX + 'gradleProp'): 1
        ])
        configurationCacheRun "help"

        then:
        outputContains '1!'
        configurationCache.assertStateStored()

        when:
        executer.withEnvironmentVars([
            (ENV_PROJECT_PROPERTIES_PREFIX + 'gradleProp'): 1
        ])
        configurationCacheRun "help"

        then:
        outputDoesNotContain '1!'
        configurationCache.assertStateLoaded()

        when:
        executer.withEnvironmentVars([
            (ENV_PROJECT_PROPERTIES_PREFIX + 'gradleProp'): 2
        ])
        configurationCacheRun "help"

        then:
        outputContains '2!'
        outputContains "because the set of environment variables prefixed by '$ENV_PROJECT_PROPERTIES_PREFIX' has changed."
        configurationCache.assertStateStored()

        when: 'the set of prefixed environment variables changes'
        executer.withEnvironmentVars([
            (ENV_PROJECT_PROPERTIES_PREFIX + 'unused'): 1,
            (ENV_PROJECT_PROPERTIES_PREFIX + 'gradleProp'): 2
        ])
        configurationCacheRun "help"

        then: 'the cache is invalidated'
        outputContains '2!'
        outputContains "because the set of environment variables prefixed by '${ENV_PROJECT_PROPERTIES_PREFIX}' has changed."
        configurationCache.assertStateStored()
    }

    def "detects dynamic Gradle property access in settings script"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            println($dynamicPropertyExpression + '!')
        """

        when:
        configurationCacheRun "help", "-PgradleProp=1", "-PunusedProperty=42"

        then:
        outputContains '1!'
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help", "-PunusedProperty=42", "-PgradleProp=1"

        then:
        outputDoesNotContain '1!'
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "help", "-PgradleProp=2"

        then:
        outputContains '2!'
        configurationCache.assertStateStored()
        outputContains "because the set of Gradle properties has changed."

        where:
        dynamicPropertyExpression << [
            'gradleProp',
            'ext.gradleProp'
        ]
    }

    @Issue("https://github.com/gradle/gradle/issues/19184")
    @NotYetImplemented
    def "system properties from included build properties file"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def fixture = new BuildLogicChangeFixture(file('build-logic'))
        fixture.setup()
        settingsFile << """
            pluginManagement {
                includeBuild 'build-logic'
            }
        """
        buildFile << """
            plugins { id('$fixture.pluginId') }

            println(providers.systemProperty('fromPropertiesFile').orNull + "!")
        """
        file('build-logic/gradle.properties') << 'systemProp.fromPropertiesFile=42'

        when:
        System.clearProperty('fromPropertiesFile')
        configurationCacheRun fixture.task

        then:
        outputContains fixture.expectedOutputBeforeChange
        outputContains '42!'
        configurationCache.assertStateStored()

        when:
        System.clearProperty('fromPropertiesFile')
        configurationCacheRun fixture.task

        then:
        outputDoesNotContain "because system property 'fromPropertiesFile' has changed"
        outputContains fixture.expectedOutputBeforeChange
        configurationCache.assertStateLoaded()

        cleanup:
        System.clearProperty('fromPropertiesFile')
    }
}
