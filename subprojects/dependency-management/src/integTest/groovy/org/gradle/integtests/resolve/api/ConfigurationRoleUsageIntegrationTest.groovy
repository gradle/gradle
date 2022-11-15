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
    def "default usage for new configuration created without role is to allow anything"() {
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

    def "usage allowed for role-based configuration #role is as intended"() {
        given:
        buildFile << """
            configurations.$createRoleCall

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
        role            | createRoleCall            || consumable  | resolvable    | declarableAgainst | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        'consumable'    | "consumable('custom')"    || true        | false         | false             | false                 | false                 | false
        'resolvable'    | "resolvable('custom')"    || false       | true          | false             | false                 | false                 | false
        'bucket'        | "bucket('custom')"        || false       | false         | true              | false                 | false                 | false
//        true        | true          | true              | false                 | true                  | true                          || ConfigurationRoles.DEPRECATED_CONSUMABLE
//        true        | true          | true              | true                  | false                 | true                          || ConfigurationRoles.DEPRECATED_RESOLVABLE
    }

    def "can prevent usage mutation of default legacy role"() {
        given:
        buildFile << """
            configurations {
                custom {
                    preventUsageMutation()
                    canBeResolved = false
                }
            }
        """

        expect:
        fails 'help'

        and:
        failure.assertHasCause("Cannot change the allowed usage of configuration: 'configuration ':custom'', as it has been locked.")
    }

//    def "warns if explicitly using a deprecated role"() {
//        given:
//        buildFile << """
//            configurations.legacy('custom')
//        """
//
//        when:
//        executer.expectDocumentedDeprecationWarning("The configuration role: Legacy is deprecated and should no longer be used. This behavior has been deprecated. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configurations_should_not_be_used")
//
//        then:
//        succeeds 'help'
//    }
}
