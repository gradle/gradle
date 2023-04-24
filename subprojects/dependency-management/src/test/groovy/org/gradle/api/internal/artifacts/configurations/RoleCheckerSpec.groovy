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

package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.GradleException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal.RoleChecker

class RoleCheckerSpec extends Specification {
    def "can check if usage is consistent with role"() {
        given:
        def configuration = Mock(ConfigurationInternal)
        configuration.isCanBeConsumed() >> consumable
        configuration.isCanBeResolved() >> resolvable
        configuration.isCanBeDeclared() >> declarable
        configuration.isDeprecatedForConsumption() >> consumptionDeprecated
        configuration.isDeprecatedForResolution() >> resolutionDeprecated
        configuration.isDeprecatedForDeclarationAgainst() >> declarationAgainstDeprecated

        expect:
        RoleChecker.isUsageConsistentWithRole(configuration, role)

        where: // These are just a sample, not all possibilities
        role                                                                                || consumable  | resolvable    | declarable | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        ConfigurationRoles.LEGACY                                                           || true        | true          | true       | false                 | false                 | false
        ConfigurationRoles.CONSUMABLE                                                       || true        | false         | false      | false                 | false                 | false
        ConfigurationRoles.RESOLVABLE_BUCKET                                                || false       | true          | true       | false                 | false                 | false
        ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_BUCKET                          || true        | true          | true       | true                  | false                 | false
        ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE                                 || true        | true          | true       | false                 | true                  | true
        ConfigurationRolesForMigration.RESOLVABLE_BUCKET_TO_RESOLVABLE                      || false       | true          | true       | false                 | false                 | true
    }

    def "can detect if usage is not consistent with role"() {
        given:
        def configuration = Mock(ConfigurationInternal)
        configuration.isCanBeConsumed() >> true
        configuration.isCanBeResolved() >> false
        configuration.isCanBeDeclared() >> false
        configuration.isDeprecatedForConsumption() >> true
        configuration.isDeprecatedForResolution() >> false
        configuration.isDeprecatedForDeclarationAgainst() >> false

        expect:
        !RoleChecker.isUsageConsistentWithRole(configuration, role)

        where: // These are just a sample, not all possibilities
        role                                                                                || consumable  | resolvable    | declarable | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        ConfigurationRoles.LEGACY                                                           || false       | true          | true              | false                 | false                 | false
        ConfigurationRoles.CONSUMABLE                                              || true        | true          | false             | false                 | false                 | false
        ConfigurationRoles.RESOLVABLE_BUCKET                                       || false       | true          | false             | false                 | false                 | false
        ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_BUCKET                 || true        | true          | true              | false                 | false                 | false
        ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE                        || true        | true          | true              | false                 | false                 | true
        ConfigurationRolesForMigration.RESOLVABLE_BUCKET_TO_RESOLVABLE    || false       | true          | true              | false                 | false                 | false
    }

    def "can assert if usage is consistent with role"() {
        given:
        def configuration = Mock(ConfigurationInternal)
        configuration.getName() >> 'custom'
        configuration.isCanBeConsumed() >> true
        configuration.isCanBeResolved() >> false
        configuration.isCanBeDeclared() >> false
        configuration.isDeprecatedForConsumption() >> false
        configuration.isDeprecatedForResolution() >> false
        configuration.isDeprecatedForDeclarationAgainst() >> false

        when:
        RoleChecker.assertIsInRole(configuration, ConfigurationRoles.RESOLVABLE)

        then:
        GradleException e = thrown()
        e.message.contains("Usage for configuration: custom is not consistent with the role: Resolvable.")
    }
}
