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

package org.gradle.internal.cc.impl

import org.gradle.internal.cc.impl.fixtures.GradlePropertiesIncludedBuildFixture
import org.gradle.internal.cc.impl.fixtures.SystemPropertiesCompositeBuildFixture
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
        outputContains "because the set of system properties prefixed by '${SYSTEM_PROJECT_PROPERTIES_PREFIX}' has changed: the value of '${SYSTEM_PROJECT_PROPERTIES_PREFIX}gradleProp' was changed."
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}gradleProp=2", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}unusedProp=2"

        then:
        outputContains '2!'
        outputContains "because the set of system properties prefixed by '${SYSTEM_PROJECT_PROPERTIES_PREFIX}' has changed: '${SYSTEM_PROJECT_PROPERTIES_PREFIX}unusedProp' was added."
        configurationCache.assertStateStored()
    }

    def "invalidates cache when set of Gradle property defining system properties has multiple changes"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            println(forChange1 + '!')
        """

        when:
        configurationCacheRun "help", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}forChange1=0", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}forChange2=1", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}forRemove1=2", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}forRemove3=3", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}forRemove2=4"

        then:
        outputContains '0!'
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}forChange1=10", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}forChange2=11", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}forAdd1=12", "-D${SYSTEM_PROJECT_PROPERTIES_PREFIX}forAdd2=13"

        then:
        outputContains '10!'
        outputContains "because the set of system properties prefixed by '${SYSTEM_PROJECT_PROPERTIES_PREFIX}' has changed: " +
            "the values of '${SYSTEM_PROJECT_PROPERTIES_PREFIX}forChange1' and '${SYSTEM_PROJECT_PROPERTIES_PREFIX}forChange2' were changed, " +
            "'${SYSTEM_PROJECT_PROPERTIES_PREFIX}forAdd1' and '${SYSTEM_PROJECT_PROPERTIES_PREFIX}forAdd2' were added, " +
            "and '${SYSTEM_PROJECT_PROPERTIES_PREFIX}forRemove1', '${SYSTEM_PROJECT_PROPERTIES_PREFIX}forRemove2', and '${SYSTEM_PROJECT_PROPERTIES_PREFIX}forRemove3' were removed."
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
        outputContains "because the set of environment variables prefixed by '$ENV_PROJECT_PROPERTIES_PREFIX' has changed: the value of '${ENV_PROJECT_PROPERTIES_PREFIX}gradleProp' was changed."
        configurationCache.assertStateStored()

        when: 'the set of prefixed environment variables changes'
        executer.withEnvironmentVars([
            (ENV_PROJECT_PROPERTIES_PREFIX + 'unused'): 1,
            (ENV_PROJECT_PROPERTIES_PREFIX + 'gradleProp'): 2
        ])
        configurationCacheRun "help"

        then: 'the cache is invalidated'
        outputContains '2!'
        outputContains "because the set of environment variables prefixed by '${ENV_PROJECT_PROPERTIES_PREFIX}' has changed: '${ENV_PROJECT_PROPERTIES_PREFIX}unused' was added."
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
        outputContains "because the set of Gradle properties has changed: the value of 'gradleProp' was changed and 'unusedProperty' was removed."

        where:
        dynamicPropertyExpression << [
            'gradleProp',
            'ext.gradleProp'
        ]
    }

    @Issue("https://github.com/gradle/gradle/issues/19793")
    def "gradle properties must be accessible from task in #build"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        build.setup(this)

        when:
        configurationCacheRun build.task()

        then:
        configurationCache.assertStateStored()
        outputContains build.expectedPropertyOutput()

        when:
        configurationCacheRun build.task()

        then:
        configurationCache.assertStateLoaded()
        outputContains build.expectedPropertyOutput()
        outputDoesNotContain "GradleProperties has not been loaded yet."

        where:
        build << GradlePropertiesIncludedBuildFixture.builds()
    }

    @Issue("https://github.com/gradle/gradle/issues/19184")
    def "system properties declared in properties of composite build"() {
        given:
        String systemProp = "fromPropertiesFile"
        def configurationCache = newConfigurationCacheFixture()
        def fixture = spec.createFixtureFor(this, systemProp)
        fixture.setup()

        when:
        System.clearProperty(systemProp)
        configurationCacheRun fixture.task

        then:
        outputContains(fixture.expectedConfigurationTimeValue())
        outputContains(fixture.expectedExecutionTimeValue())

        when:
        System.clearProperty(systemProp)
        configurationCacheRun fixture.task

        then:
        configurationCache.assertStateLoaded()
        outputContains(fixture.expectedExecutionTimeValue())

        cleanup:
        System.clearProperty(systemProp)

        where:
        spec << SystemPropertiesCompositeBuildFixture.specsWithSystemPropertyAccess()
    }

    def "passing cli override doesn't invalidates cache entry if property wasn't read at configuration time"() {
        given:
        String systemProp = "fromPropertiesFile"
        def configurationCache = newConfigurationCacheFixture()
        def fixture = spec.createFixtureFor(this, systemProp)
        fixture.setup()

        when:
        System.clearProperty(systemProp)
        configurationCacheRun fixture.task

        System.clearProperty(systemProp)
        configurationCacheRun fixture.task

        then:
        configurationCache.assertStateLoaded()

        when:
        System.clearProperty(systemProp)
        configurationCacheRun fixture.task, "-D$systemProp=overridden value"

        then:
        configurationCache.assertStateLoaded()
        outputContains("Execution: overridden value")

        cleanup:
        System.clearProperty(systemProp)

        where:
        spec << SystemPropertiesCompositeBuildFixture.specsWithoutSystemPropertyAccess()
    }

    def "passing cli override invalidates cache entry if property was read at configuration time"() {
        given:
        String systemProp = "fromPropertiesFile"
        def configurationCache = newConfigurationCacheFixture()
        def fixture = spec.createFixtureFor(this, systemProp)
        fixture.setup()

        when:
        System.clearProperty(systemProp)
        configurationCacheRun fixture.task

        System.clearProperty(systemProp)
        configurationCacheRun fixture.task

        then:
        configurationCache.assertStateLoaded()

        when:
        System.clearProperty(systemProp)
        configurationCacheRun fixture.task, "-D$systemProp=overridden value"

        then:
        outputContains("because system property '$systemProp' has changed")
        outputContains("Execution: overridden value")

        when:
        System.clearProperty(systemProp)
        configurationCacheRun fixture.task, "-D$systemProp=overridden value"

        then:
        configurationCache.assertStateLoaded()
        outputContains("Execution: overridden value")

        cleanup:
        System.clearProperty(systemProp)

        where:
        spec << SystemPropertiesCompositeBuildFixture.specsWithSystemPropertyAccess()
    }

    def "runtime mutation system properties in composite build"() {
        given:
        String systemProp = "fromPropertiesFile"
        def configurationCache = newConfigurationCacheFixture()
        def includedBuildSystemPropertyDefinition = new SystemPropertiesCompositeBuildFixture.IncludedBuild("included-build")
        def spec = new SystemPropertiesCompositeBuildFixture.Spec(
            [includedBuildSystemPropertyDefinition],
            SystemPropertiesCompositeBuildFixture.SystemPropertyAccess.NO_ACCESS
        )

        def fixture = spec.createFixtureFor(this, systemProp)
        fixture.setup()

        settingsFile << "System.setProperty('$systemProp', 'mutated value')\n"

        when:
        System.clearProperty(systemProp)
        configurationCacheRun fixture.task

        then:
        outputContains("Execution: ${includedBuildSystemPropertyDefinition.propertyValue()}")

        when:
        System.clearProperty(systemProp)
        configurationCacheRun fixture.task

        then:
        configurationCache.assertStateLoaded()
        outputContains("Execution: ${includedBuildSystemPropertyDefinition.propertyValue()}")

        cleanup:
        System.clearProperty(systemProp)
    }
}
