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

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.cc.impl.fixtures.GradlePropertiesIncludedBuildFixture
import org.gradle.internal.cc.impl.fixtures.SystemPropertiesCompositeBuildFixture
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

import static org.gradle.initialization.properties.GradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.properties.GradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

class ConfigurationCacheGradlePropertiesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = newConfigurationCacheFixture()

    def "invalidates cache when set of Gradle property defining system properties changes"() {
        given:
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
        outputContains "configuration cache cannot be reused because Gradle property 'gradleProp' has changed."

        where:
        dynamicPropertyExpression << [
            'gradleProp',
            'ext.gradleProp'
        ]
    }

    @Issue("https://github.com/gradle/gradle/issues/19793")
    def "gradle properties must be accessible from task in #build"() {
        given:
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

    def "reuses cache when system property used only at execution time changes"() {
        given:
        String systemProp = "fromPropertiesFile"
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

    def "invalidates cache when system property used at configuration time changes"() {
        given:
        String systemProp = "fromPropertiesFile"
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

    def "reuses cache when unused project property changes on command-line"() {
        buildFile """
            tasks.register("some")
        """

        when:
        configurationCacheRun "some", "-Pfoo=one"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "some", "-Pfoo=two"

        then:
        configurationCache.assertStateLoaded()
    }

    def "invalidates cache when project property changes on command-line, if used at configuration time via #description"() {
        buildFile """
            tasks.register("some")
        """

        file(fileWithAccess) << """
            def access = ${accessExpr}
            println("Access: '\${access}'")
        """

        when:
        configurationCacheRun "some", "-Pfoo=one"

        then:
        configurationCache.assertStateStored()
        outputContains("Access: '${isPresence ? 'true' : 'one'}'")

        when:
        configurationCacheRun "some", "-Pfoo=two"

        then:
        configurationCache.assertStateStored()
        outputContains("Access: '${isPresence ? 'true' : 'two'}'")

        where:
        description                        | fileWithAccess    | accessExpr                                                   | isPresence
        "project.ext.get()"                | "build.gradle"    | "project.ext.get('foo')"                                     | false
        "project.ext.has()"                | "build.gradle"    | "project.ext.has('foo')"                                     | true
        "project.ext.properties"           | "build.gradle"    | "project.ext.properties.foo"                                 | false
        "project.ext.foo"                  | "build.gradle"    | "project.ext.foo"                                            | false
        "project.foo"                      | "build.gradle"    | "project.foo"                                                | false
        "project script lookup"            | "build.gradle"    | "foo"                                                        | false
        "project.hasProperty()"            | "build.gradle"    | "project.hasProperty('foo')"                                 | true
        "startParameter.projectProperties" | "build.gradle"    | "gradle.startParameter.projectProperties['foo']"             | false
        "startParameter.projectProperties" | "build.gradle"    | "gradle.startParameter.projectProperties.containsKey('foo')" | true
        "settings.ext.get()"               | "settings.gradle" | "settings.ext.get('foo')"                                    | false
        "settings.ext.has()"               | "settings.gradle" | "settings.ext.has('foo')"                                    | true
        "settings.ext.properties"          | "settings.gradle" | "settings.ext.properties.foo"                                | false
        "settings.ext.foo"                 | "settings.gradle" | "settings.ext.foo"                                           | false
        "settings.foo"                     | "settings.gradle" | "settings.foo"                                               | false
        "settings script lookup"           | "settings.gradle" | "foo"                                                        | false
    }

    @ToBeImplemented
    def "reuses cache when unused project property changes on command-line, if accessing #description"() {
        buildFile """
            // access another property that is not changing
            ${accessExpr}

            tasks.register("some")
        """

        when:
        configurationCacheRun "some", "-Pbar=bar", "-Pfoo=one"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "some", "-Pbar=bar", "-Pfoo=two"

        then:
        configurationCache.assertStateStored()
        // Must be:
//        configurationCache.assertStateLoaded()

        where:
        description                                 | accessExpr
        "ext.properties property"                   | "ext.properties.bar"
        "startParameter.projectProperties property" | "gradle.startParameter.projectProperties['bar']"
        "startParameter.projectProperties"          | "gradle.startParameter.projectProperties"
    }

    def "reuses cache when project property, accessed via ext, changes on command-line, but was shadowed via build logic assignment"() {
        buildFile """
            project.ext.foo = "script"
            println("Access: '\${project.ext.foo}'")

            tasks.register("some")
        """

        when:
        configurationCacheRun "some", "-Pfoo=one"

        then:
        configurationCache.assertStateStored()
        outputContains("Access: 'script'")

        when:
        configurationCacheRun "some", "-Pfoo=two"

        then:
        configurationCache.assertStateLoaded()
    }

    def "reuses cache when project property changes on command-line, if used only at execution time via extra container"() {
        buildFile """
            def props = project.ext
            tasks.register("some") {
                doLast {
                    println("Execution: '\${props.foo}'")
                }
            }
        """

        when:
        configurationCacheRun "some", "-Pfoo=one"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "some", "-Pfoo=two"

        then:
        configurationCache.assertStateLoaded()
        outputContains "Execution: 'two'"
    }

    def "reuses cache when project property changes on command-line, if used only at execution time via gradleProperty"() {
        buildFile """
            def prop = providers.gradleProperty('foo')
            tasks.register("some") {
                doLast {
                    println("Execution: '\${prop.orNull}'")
                }
            }
        """

        when:
        configurationCacheRun "some", "-Pfoo=one"

        then:
        configurationCache.assertStateStored()
        outputContains("Execution: 'one'")

        when:
        configurationCacheRun "some", "-Pfoo=two"

        then:
        configurationCache.assertStateLoaded()
        outputContains("Execution: 'two'")
    }

    def "reuses cache when project property changes on command-line, if looked up but unused at configuration time via gradleProperty"() {
        buildFile """
            providers.gradleProperty("foo")
            tasks.register("some")
        """

        when:
        configurationCacheRun "some", "-Pfoo=one"
        then:
        executed(":some")
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "some", "-Pfoo=two"
        then:
        configurationCache.assertStateLoaded()
    }

    def "invalidates cache when project property changes on command-line, if used at configuration time as gradle property via #description"() {
        buildFile """
            tasks.register("some")
        """

        file(fileWithAccess) << """
            def access = ${accessExpr}
            println("Access: '\${access}'")
        """

        when:
        configurationCacheRun "some", "-Pfoo=one"

        then:
        configurationCache.assertStateStored()
        outputContains("Access: '${isPresence ? 'true' : 'one'}'")

        when:
        configurationCacheRun "some", "-Pfoo=two"

        then:
        configurationCache.assertStateStored()
        outputContains("Access: '${isPresence ? 'true' : 'two'}'")

        when:
        configurationCacheRun "some" // no property

        then:
        configurationCache.assertStateStored()
        outputContains("Access: '${isPresence ? 'false' : 'null'}'")

        where:
        description                                     | fileWithAccess    | accessExpr                                    | isPresence
        "project.providers.gradleProperty()"            | "build.gradle"    | "providers.gradleProperty('foo').orNull"      | false
        "project.providers.gradleProperty().isPresent"  | "build.gradle"    | "providers.gradleProperty('foo').isPresent()" | true
        "settings.providers.gradleProperty()"           | "settings.gradle" | "providers.gradleProperty('foo').orNull"      | false
        "settings.providers.gradleProperty().isPresent" | "settings.gradle" | "providers.gradleProperty('foo').isPresent()" | true
    }

    def "fine-grained property tracking can be disabled via #source"() {
        given:
        settingsFile.touch()
        switch (source) {
            case 'command-line':
                executer.beforeExecute {
                    withArgument "-D${StartParameterBuildOptions.ConfigurationCacheFineGrainedPropertyTracking.PROPERTY_NAME}=false"
                }
                break
            case 'gradle.properties':
                propertiesFile.writeProperties(
                    (StartParameterBuildOptions.ConfigurationCacheFineGrainedPropertyTracking.PROPERTY_NAME): "false"
                )
                break
            default:
                assert false
        }

        when:
        configurationCacheRun "help", "-PgradleProp=1", "-PunusedProperty=42"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help", "-PunusedProperty=42", "-PgradleProp=1"

        then:
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "help", "-PgradleProp=2"

        then:
        configurationCache.assertStateStored()
        outputContains "because the set of Gradle properties has changed: the value of 'gradleProp' was changed and 'unusedProperty' was removed."

        where:
        source << ['command-line', 'gradle.properties']
    }

    def "reuses cache when start parameter project property used at execution time changes"() {
        given:
        buildFile """
            abstract class FooTask extends DefaultTask {
                @Inject
                abstract StartParameter getStartParameter()

                @TaskAction
                void foo() {
                    println("Bar: \${startParameter.projectProperties['bar']}")
                }
            }

            tasks.register("foo", FooTask)
        """

        when:
        configurationCacheRun "foo", "-Pbar=one"

        then:
        configurationCache.assertStateStored()
        outputContains("Bar: one")

        when:
        configurationCacheRun "foo", "-Pbar=two"

        then:
        configurationCache.assertStateLoaded()
        outputContains("Bar: two")
    }

    def "invalidates cache when project property changes on command-line, if used at configuration time in composite #included build"() {
        if (included != "buildSrc") {
            settingsFile """
                includeBuild('$included')
            """
        }
        buildFile("$included/build.gradle", """
            def access = gradle.startParameter.projectProperties["foo"]
            println("Access: '\${access}'")
        """)

        when:
        configurationCacheRun "help", "-Pfoo=one"

        then:
        configurationCache.assertStateStored()
        outputContains("Access: 'null'")

        when:
        configurationCacheRun "help", "-Pfoo=two"

        then:
        configurationCache.assertStateStored()
        outputContains("Access: 'null'")

        where:
        included << ["buildSrc", "included"]
    }

    def "invalidates cache when project property changes on command-line, if used only at execution time via start parameter"() {
        given:
        buildFile """
            tasks.register("foo") {
                def projectProps = gradle.startParameter.projectProperties
                doLast {
                    println("Bar: \${projectProps['bar']}")
                }
            }
        """

        when:
        configurationCacheRun "foo", "-Pbar=one"

        then:
        configurationCache.assertStateStored()
        outputContains "Bar: one"

        when:
        configurationCacheRun "foo", "-Pbar=two"

        then:
        configurationCache.assertStateStored()
        outputContains "Bar: two"
    }

    @ToBeImplemented
    def "invalidates cache when project property changes on command-line, if used only at execution time via captured start parameter"() {
        given:
        buildFile """
            tasks.register("foo") {
                def captured = gradle.startParameter
                doLast {
                    println("Bar: \${captured.projectProperties['bar']}")
                }
            }
        """

        when:
        configurationCacheRun "foo", "-Pbar=one"

        then:
        configurationCache.assertStateStored()
        outputContains "Bar: one"

        when:
        configurationCacheRun "foo", "-Pbar=two"

        then:
        configurationCache.assertStateLoaded()
        outputContains "Bar: one"
        // Must be cache miss
    }
}
