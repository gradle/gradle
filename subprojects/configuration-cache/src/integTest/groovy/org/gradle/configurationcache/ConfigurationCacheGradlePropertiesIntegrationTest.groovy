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

import org.gradle.configurationcache.fixtures.GradlePropertiesIncludedBuildFixture
import org.gradle.configurationcache.fixtures.SystemPropertiesCompositeBuildFixture
import org.gradle.configurationcache.isolated.IsolatedProjectsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.Flaky
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

@Flaky(because = 'https://github.com/gradle/gradle-private/issues/3859')
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

    @Issue("https://github.com/gradle/gradle/issues/26049")
    @IgnoreIf({ GradleContextualExecuter.isolatedProjects }) // Isolated Projects option is explicitly controlled by the test
    def "invalidates cache when Isolated Projects property is changed"() {
        def configurationCache = newConfigurationCacheFixture()
        def isolatedProjects = new IsolatedProjectsFixture(this)

        when:
        configurationCacheRun "help"
        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help"
        then:
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "help", "-Dorg.gradle.unsafe.isolated-projects=true"
        then:
        configurationCache.assertStateStored()
        isolatedProjects.assertStateStored {
            storeReason = "Calculating task graph as configuration cache cannot be reused because the Isolated Projects option has changed."
            projectConfigured(":")
        }

        when:
        configurationCacheRun "help", "-Dorg.gradle.unsafe.isolated-projects=true"
        then:
        configurationCache.assertStateLoaded()
        isolatedProjects.assertStateLoaded()
    }
}
