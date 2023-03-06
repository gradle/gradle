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

import spock.lang.Specification

import static UsageDescriber.*

class ConfigurationRoleSpec extends Specification {
    def "can find predefined role #role"() {
        expect:
        ConfigurationRole.forUsage(consumable, resolvable, declarableAgainst, consumptionDeprecated, resolutionDeprecated, declarationAgainstDeprecated) == role

        where:
        consumable  | resolvable    | declarableAgainst | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated  || role
        true        | true          | true              | false                 | false                 | false                         || ConfigurationRoles.LEGACY
        true        | false         | false             | false                 | false                 | false                         || ConfigurationRoles.INTENDED_CONSUMABLE
        false       | true          | false             | false                 | false                 | false                         || ConfigurationRoles.INTENDED_RESOLVABLE
        false       | true          | true              | false                 | false                 | false                         || ConfigurationRoles.INTENDED_RESOLVABLE_BUCKET
        false       | false         | true              | false                 | false                 | false                         || ConfigurationRoles.INTENDED_BUCKET
        true        | true          | true              | false                 | true                  | true                          || ConfigurationRoles.DEPRECATED_CONSUMABLE
        true        | true          | true              | true                  | false                 | true                          || ConfigurationRoles.DEPRECATED_RESOLVABLE
    }

    def "can create role for unknown usage combinations consumable=#consumable, resolvable=#resolvable, declarableAgainst=#declarableAgainst, consumptionDeprecated=#consumptionDeprecated, resolutionDeprecated=#resolutionDeprecated, declarationAgainstDeprecated=#declarationAgainstDeprecated"() {
        expect:
        ConfigurationRole role = ConfigurationRole.forUsage(consumable, resolvable, declarableAgainst, consumptionDeprecated, resolutionDeprecated, declarationAgainstDeprecated)
        role.isConsumable() == consumable
        role.isResolvable() == resolvable
        role.isDeclarableAgainst() == declarableAgainst
        role.isConsumptionDeprecated() == consumptionDeprecated
        role.isResolutionDeprecated() == resolutionDeprecated
        role.isDeclarationAgainstDeprecated() == declarationAgainstDeprecated

        where:
        consumable  | resolvable    | declarableAgainst | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        true        | true          | false             | false                 | false                 | false
        false       | true          | true              | false                 | true                  | false
        true        | true          | true              | true                  | true                  | true
        true        | false         | true              | true                  | false                 | true
        true        | true          | true              | false                 | true                  | false
    }

    def "custom role is named correctly"() {
        when:
        def customRole = ConfigurationRole.forUsage(consumable, resolvable, declarableAgainst, consumptionDeprecated, resolutionDeprecated, declarationAgainstDeprecated)

        then:
        customRole !in ConfigurationRoles.values()
        customRole.name == DEFAULT_CUSTOM_ROLE_NAME

        where:
        consumable  | resolvable    | declarableAgainst | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        true        | true          | false             | false                 | false                 | false
        false       | true          | true              | false                 | true                  | false
        true        | true          | true              | true                  | true                  | true
        true        | false         | true              | true                  | false                 | true
        true        | true          | true              | false                 | true                  | false
    }

    def "custom role can be given custom description"() {
        when:
        def customRole = ConfigurationRole.forUsage('custom', consumable, resolvable, declarableAgainst, consumptionDeprecated, resolutionDeprecated, declarationAgainstDeprecated, 'custom description', false)

        then:
        customRole !in ConfigurationRoles.values()
        customRole.name == 'custom'
        customRole.describeUsage() == 'custom description'

        where:
        consumable  | resolvable    | declarableAgainst | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        true        | true          | false             | false                 | false                 | false
        false       | true          | true              | false                 | true                  | false
        true        | true          | true              | true                  | true                  | true
        true        | false         | true              | true                  | false                 | true
        true        | true          | true              | false                 | true                  | false
    }

    def "roles can describe themselves #role"() {
        expect:
        assertDescriptionContains(role, usages)

        where:
        role                                            || usages
        ConfigurationRoles.LEGACY                       || [CONSUMABLE, RESOLVABLE, DECLARABLE_AGAINST]
        ConfigurationRoles.INTENDED_CONSUMABLE          || [CONSUMABLE]
        ConfigurationRoles.INTENDED_RESOLVABLE          || [RESOLVABLE]
        ConfigurationRoles.INTENDED_RESOLVABLE_BUCKET   || [RESOLVABLE, DECLARABLE_AGAINST]
        ConfigurationRoles.INTENDED_BUCKET              || [DECLARABLE_AGAINST]
        ConfigurationRoles.DEPRECATED_CONSUMABLE        || [CONSUMABLE, deprecatedFor(RESOLVABLE), deprecatedFor(DECLARABLE_AGAINST)]
        ConfigurationRoles.DEPRECATED_RESOLVABLE        || [RESOLVABLE, deprecatedFor(CONSUMABLE), deprecatedFor(DECLARABLE_AGAINST)]
    }

    def "custom role can't deprecate what it doesn't allow"() {
        when:
        ConfigurationRole.forUsage(consumable, resolvable, declarableAgainst, consumptionDeprecated, resolutionDeprecated, declarationAgainstDeprecated)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot create a role that deprecates a usage that is not allowed'

        where:
        consumable  | resolvable    | declarableAgainst | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        false       | false         | false             | true                  | false                 | false
        false       | false         | false             | false                 | true                  | false
        false       | false         | false             | false                 | false                 | true
    }

    private String deprecatedFor(String usage) {
        return usage + describeDeprecation(true)
    }

    private void assertDescriptionContains(ConfigurationRole role, List<String> usages) {
        for (String usage : usages) {
            assert role.describeUsage().contains(usage)
        }
    }
}
