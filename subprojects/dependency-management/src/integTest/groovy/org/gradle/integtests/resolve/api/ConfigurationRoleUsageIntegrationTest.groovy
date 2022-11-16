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

    def "can prevent usage mutation of roleless configuration #configuration added by java plugin meant for consumption"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            configurations {
                $configuration {
                    assert canBeConsumed == true
                    preventUsageMutation()
                    canBeConsumed = false
                }
            }
        """

        expect:
        fails 'help'

        and:
        assertUsageLockedFailure(configuration, 'Intended Consumable')

        where:
        configuration << ['runtimeElements', 'apiElements']
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
                bucket('bucket1')
                deprecatedConsumable('deprecatedConsumable1')
                deprecatedResolvable('deprecatedResolvable1')

                consumable('consumable2', true)
                resolvable('resolvable2', true)
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
    // endregion Role-Based Configurations

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
    // endregion Custom Roles

    private void assertUsageLockedFailure(String configurationName, String roleName = null) {
        String suffix = roleName ? "as it was locked upon creation to the role: '$roleName'." : "as it has been locked."
        failure.assertHasCause("Cannot change the allowed usage of configuration ':$configurationName', $suffix")
    }
}
