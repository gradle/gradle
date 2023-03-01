/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.resolve.api


import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ConfigurationRoleUsageIntegrationTest extends AbstractIntegrationSpec {
    // region Roleless (Implicit LEGACY Role) Configurations
    def "default usage for roleless configuration is to allow anything"() {
        given:
        buildFile << """
            configurations {
                custom
            }

            tasks.register('checkConfUsage') {
                doLast {
                    assert configurations.custom.canBeConsumed
                    assert configurations.custom.canBeResolved
                    assert configurations.custom.canBeDeclaredAgainst
                    assert !configurations.custom.deprecatedForConsumption
                    assert !configurations.custom.deprecatedForResolution
                    assert !configurations.custom.deprecatedForDeclarationAgainst
                }
            }
        """

        expect:
        succeeds('checkConfUsage')
    }

    def "can create configuration named #configuration with same legacy behavior"() {
        given:
        buildFile << """
            configurations {
                $configuration {
                    assert canBeConsumed
                    assert canBeResolved
                    assert canBeDeclaredAgainst
                    assert !deprecatedForConsumption
                    assert !deprecatedForResolution
                    assert !deprecatedForDeclarationAgainst
                }
            }
        """

        expect:
        succeeds 'help'

        where:
        configuration << ConfigurationRoles.values().collect {
            def name = it.name.replace(' ', '')
            return name[0].toLowerCase() + name[1..-1]
        }
    }

    def "can prevent usage mutation of roleless configurations"() {
        given:
        buildFile << """
            configurations {
                custom {
                    assert canBeResolved == true
                    preventUsageMutation()
                    canBeResolved = false
                }
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure('custom')
    }

    def "can prevent usage mutation of roleless configuration meant for consumption"() {
        given:
        buildFile << """
            configurations {
                testConf {
                    canBeConsumed = true
                    canBeResolved = false
                    canBeDeclaredAgainst = false
                    preventUsageMutation()
                    canBeConsumed = false
                }
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure('testConf')
    }

    def "can prevent usage mutation of roleless configuration #configuration added by java plugin meant for resolution"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            configurations {
                $configuration {
                    assert canBeResolved == true
                    preventUsageMutation()
                    canBeResolved = false
                }
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure(configuration, 'Intended Resolvable')

        where:
        configuration << ['runtimeClasspath', 'compileClasspath']
    }

    def "configurations created by buildSrc automatically can have usage changed"() {
        given:
        file("buildSrc/src/main/java/MyTask.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public abstract class MyTask extends DefaultTask {
                @TaskAction
                void run() {}
            }
        """

        file("buildSrc/build.gradle") << """
            configurations {
                assert findByName('implementation')

                implementation {
                    canBeConsumed = !canBeConsumed
                    canBeResolved = !canBeResolved
                    canBeDeclaredAgainst = !canBeDeclaredAgainst
                }
            }
        """

        buildFile << """
            tasks.register('myTask', MyTask)
        """

        expect:
        succeeds 'myTask'
    }

    def "configurations can have usage changed from other projects"() {
        given:
        file("projectA/build.gradle") << """
            plugins {
                id 'java'
            }
        """

        file("projectB/build.gradle") << """
            plugins {
                id 'java'
            }
        """

        file("settings.gradle") << """
            include 'projectA', 'projectB'
        """

        file("build.gradle") << """
            subprojects {
                afterEvaluate {
                    configurations {
                        println project.name
                        assert findByName('implementation')

                        implementation {
                            canBeConsumed = !canBeConsumed
                            canBeResolved = !canBeResolved
                            canBeDeclaredAgainst = !canBeDeclaredAgainst
                        }
                    }
                }
            }
        """

        expect:
        succeeds 'help'
    }
    // endregion Roleless (Implicit LEGACY Role) Configurations

    // region Role-Based Configurations
    def "intended usage is allowed for role-based configuration #role"() {
        given:
        buildFile << """
            configurations.$customRoleBasedConf

            tasks.register('checkConfUsage') {
                doLast {
                    assert configurations.custom.canBeConsumed == $consumable
                    assert configurations.custom.canBeResolved == $resolvable
                    assert configurations.custom.canBeDeclaredAgainst == $declarableAgainst
                    assert configurations.custom.deprecatedForConsumption == $consumptionDeprecated
                    assert configurations.custom.deprecatedForResolution == $resolutionDeprecated
                    assert configurations.custom.deprecatedForDeclarationAgainst == $declarationAgainstDeprecated
                }
            }
        """

        expect:
        succeeds('checkConfUsage')

        where:
        role                    | customRoleBasedConf               || consumable  | resolvable    | declarableAgainst | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        'consumable'            | "consumable('custom')"            || true        | false         | false             | false                 | false                 | false
        'resolvable'            | "resolvable('custom')"            || false       | true          | false             | false                 | false                 | false
        'resolvableBucket'      | "resolvableBucket('custom')"      || false       | true          | true              | false                 | false                 | false
        'bucket'                | "bucket('custom')"                || false       | false         | true              | false                 | false                 | false
        'deprecated consumable' | "deprecatedConsumable('custom')"  || true        | true          | true              | false                 | true                  | true
        'deprecated resolvable' | "deprecatedResolvable('custom')"  || true        | true          | true              | true                  | false                 | true
    }

    def "can prevent usage mutation of role-based configuration #role"() {
        given:
        buildFile << """
            configurations.$customRoleBasedConf

            configurations.custom {
                preventUsageMutation()
                canBeResolved = !canBeResolved
            }
        """
        executer.noDeprecationChecks()

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure('custom', displayName)

        where:
        role                    | customRoleBasedConf               | displayName
        'consumable'            | "consumable('custom')"            | 'Intended Consumable'
        'resolvable'            | "resolvable('custom')"            | 'Intended Resolvable'
        'bucket'                | "bucket('custom')"                | 'Intended Bucket'
        'deprecated consumable' | "deprecatedConsumable('custom')"  | 'Deprecated Consumable'
        'deprecated resolvable' | "deprecatedResolvable('custom')"  | 'Deprecated Resolvable'
    }

    def "exhaustively try all new role-based creation syntax"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations {
                consumable('consumable1')
                resolvable('resolvable1')
                resolvableBucket('resolvableBucket1')
                bucket('bucket1')
                deprecatedConsumable('deprecatedConsumable1')
                deprecatedResolvable('deprecatedResolvable1')

                consumable('consumable2', true)
                resolvable('resolvable2', true)
                resolvableBucket('resolvableBucket2', true)
                bucket('bucket2', true)
                deprecatedConsumable('deprecatedConsumable2', true)
                deprecatedResolvable('deprecatedResolvable2', true)

                createWithRole('consumable3', ConfigurationRoles.INTENDED_CONSUMABLE)
                createWithRole('consumable4', ConfigurationRoles.INTENDED_CONSUMABLE, true)
                createWithRole('consumable5', ConfigurationRoles.INTENDED_CONSUMABLE, true) {
                    visible = false
                }
                createWithRole('consumable6', ConfigurationRoles.INTENDED_CONSUMABLE) {
                    visible = false
                }

                maybeCreateWithRole('resolvable7', ConfigurationRoles.INTENDED_RESOLVABLE, true, true)
            }
        """

        expect:
        succeeds 'help'
    }

    def "maybeCreateWithRole reuses existing roles"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            plugins {
                id 'java'
            }

            configurations {
                def existing = findByName('implementation')
                def result = maybeCreateWithRole('implementation', ConfigurationRoles.LEGACY, false, false)
                assert result == existing
            }
        """

        expect:
        succeeds 'help'
    }

    def "maybeCreateWithRole verifies usage of existing built-in roles when match is found, succeeding on match"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            plugins {
                id 'java'
            }

            configurations {
                assert findByName('implementation')
                assert maybeCreateWithRole('implementation', ConfigurationRoles.INTENDED_BUCKET, false, true)
            }
        """

        expect:
        succeeds 'help'
    }

    def "maybeCreateWithRole verifies usage of existing built-in configurations' roles when matching configuration is found, failing on mismatch"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            plugins {
                id 'java'
            }

            configurations {
                assert findByName('implementation')
                maybeCreateWithRole('implementation', ConfigurationRoles.INTENDED_RESOLVABLE, false, true)
            }
        """

        expect:
        fails 'help'
        result.assertHasErrorOutput("""Usage for configuration: implementation is not consistent with the role: Intended Resolvable.
  Expected that it is:
  \tResolvable - this configuration can be resolved by this project to a set of files
  But is actually is:
  \tDeclarable Against - this configuration can have dependencies added to it""")
    }

    def "maybeCreateWithRole verifies usage of existing custom configurations' roles when matching configuration is found, failing on mismatch"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations {
                consumable('custom')
                assert findByName('custom')
                maybeCreateWithRole('custom', ConfigurationRoles.INTENDED_RESOLVABLE, false, true)
            }
        """

        expect:
        fails 'help'
        result.assertHasErrorOutput("""Usage for configuration: custom is not consistent with the role: Intended Resolvable.
  Expected that it is:
  \tResolvable - this configuration can be resolved by this project to a set of files
  But is actually is:
  \tConsumable - this configuration can be selected by another project as a dependency""")
    }

    def "maybeCreateWithRole can lock new roles"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations {
                assert !findByName('custom')
                def result = maybeCreateWithRole('custom', ConfigurationRoles.INTENDED_RESOLVABLE, true, false)
                assert !result.isUsageMutable()
            }
        """

        expect:
        succeeds 'help'
    }

    def "maybeCreateWithRole can lock existing roles"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            plugins {
                id 'java'
            }

            configurations {
                def existing = findByName('implementation')
                assert existing.isUsageMutable()
                def result = maybeCreateWithRole('implementation', ConfigurationRoles.LEGACY, true, false)
                assert !result.isUsageMutable()
            }
        """

        expect:
        succeeds 'help'
    }

    def "can update all roles for non-locked configurations"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles

            configurations {
                def c1 = createWithRole('c1', ConfigurationRoles.LEGACY)
                def c2 = createWithRole('c2', ConfigurationRoles.INTENDED_CONSUMABLE)
                def c3 = createWithRole('c3', ConfigurationRoles.INTENDED_RESOLVABLE)
                def c4 = createWithRole('c4', ConfigurationRoles.INTENDED_RESOLVABLE_BUCKET)
                def c5 = createWithRole('c5', ConfigurationRoles.INTENDED_BUCKET)
                def c6 = createWithRole('c6', ConfigurationRoles.DEPRECATED_CONSUMABLE)
                def c7 = createWithRole('c7', ConfigurationRoles.DEPRECATED_RESOLVABLE)
            }

            configurations.all {
                canBeResolved = !canBeResolved
                canBeConsumed = !canBeConsumed
                canBeDeclaredAgainst = !canBeDeclaredAgainst
            }
        """

        expect:
        succeeds 'help'
    }

    def "locked configurations' usage can not be updated with all"() {
        given:
        buildFile << """
            configurations {
                consumable('custom', true)
            }

            configurations.all {
                canBeResolved = !canBeResolved
                canBeConsumed = !canBeConsumed
                canBeDeclaredAgainst = !canBeDeclaredAgainst
            }
        """

        expect:
        fails 'help'
        assertUsageLockedFailure('custom', 'Intended Consumable')
    }
    // endregion Role-Based Configurations

    // region Logging
    def "changing usage is logged as deprecation if requested for #usageName"() {
        given:
        buildFile << """
            configurations {
                def custom = consumable('custom')
                assert !custom.canBeResolved
                custom.setWarnOnChangingUsage(true)
                custom.canBeResolved = true
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Allowed usage is changing for configuration ':custom', resolvable was false and is now true. Ideally, usage should be fixed upon creation. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Usage should be fixed upon creation. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        succeeds 'help'
    }

    def "using a reserved configuration name emits a deprecation warning if JavaBasePlugin applied"() {
        given:
        file("buildSrc/src/main/groovy/MyPlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.plugins.BasePlugin
            import org.gradle.api.plugins.JvmEcosystemPlugin

            class MyPlugin implements Plugin {
                void apply(project) {
                    project.pluginManager.apply(BasePlugin.class)
                    project.pluginManager.apply(JvmEcosystemPlugin.class)

                    project.sourceSets.create('custom')
                    project.configurations.create('customCompileOnly')

                    project.configurations.create('implementation')
                }
            }
        """

        file('buildSrc/build.gradle') << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """

        file("buildSrc/src/main/resources/META-INF/gradle-plugins/my-plugin.properties") << "implementation-class=MyPlugin"

        buildFile << """
            apply plugin: 'my-plugin'
            apply plugin: 'java'
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The configuration customCompileOnly was created explicitly. This configuration name is reserved for creation by Gradle. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Do not create a configuration with this name. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        executer.expectDocumentedDeprecationWarning("The configuration implementation was created explicitly. This configuration name is reserved for creation by Gradle. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Do not create a configuration with this name. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
        succeeds 'help'
    }
    // endregion Logging

    // region Custom Roles
    def "can create configuration with custom role"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            ConfigurationRole customRole = ConfigurationRole.forUsage('custom', true, true, false, false, false, false)

            configurations.createWithRole('custom', customRole) {
                assert canBeConsumed
                assert canBeResolved
                assert !canBeDeclaredAgainst
                assert !deprecatedForConsumption
                assert !deprecatedForResolution
                assert !deprecatedForDeclarationAgainst
            }
        """

        expect:
        succeeds 'help'
    }

    def "can prevent usage mutation for configuration with custom role"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            ConfigurationRole customRole = ConfigurationRole.forUsage('custom', true, true, false, false, false, false)

            configurations {
                createWithRole('custom', customRole) {
                    assert canBeConsumed
                    preventUsageMutation()
                    canBeConsumed = false
                }
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure('custom', 'custom')
    }


    def "custom role warns on creation if asked"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.configurations.ConfigurationRole

            ConfigurationRole customRole = ConfigurationRole.forUsage('custom', $consumable, $resolvable, $declarableAgainst, $consumptionDeprecated, $resolutionDeprecated, $declarationAgainstDeprecated, null, $warn)
        """
        if (warn) {
            executer.expectDocumentedDeprecationWarning("Custom configuration roles are deprecated. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Use one of the standard roles defined in ConfigurationRoles instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#custom_configuration_roles")
        }

        expect:
        succeeds 'help'

        where:
        consumable  | resolvable    | declarableAgainst | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated | warn
        true        | true         | false             | false                 | false                 | false                        | true
        true        | true         | false             | false                 | false                 | false                        | false
    }
    // endregion Custom Roles

    private void assertUsageLockedFailure(String configurationName, String roleName = null) {
        String suffix = roleName ? "as it was locked upon creation to the role: '$roleName'." : "as it has been locked."
        failure.assertHasCause("Cannot change the allowed usage of configuration ':$configurationName', $suffix")
    }
}
