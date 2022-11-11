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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ConfigurationRoleUsageIntegrationTest extends AbstractIntegrationSpec {
    def "default usage for new configuration created without role is to allow anything including mutation usage"() {
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

    def "warns if explicitly using a deprecated role"() {
        given:
        buildFile << """
            configurations.createWithRole('custom', org.gradle.api.internal.artifacts.configurations.ConfigurationRoles.LEGACY)
        """

        when:
        executer.expectDocumentedDeprecationWarning("The configuration role: Legacy is deprecated and should no longer be used. This behavior has been deprecated. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configurations_should_not_be_used")

        then:
        succeeds 'help'
    }
}
