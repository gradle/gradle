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

class ConfigurationRolesSpec extends Specification {
    def "can find predefined role #role"() {
        when:
        def result = ConfigurationRoles.byUsage(consumable, resolvable, declarableAgainst)

        then:
        result.isPresent()
        result.get() == role

        where:
        consumable  | resolvable    | declarableAgainst || role
        true        | true          | true              || ConfigurationRoles.LEGACY
        true        | false         | false             || ConfigurationRoles.INTENDED_CONSUMABLE
        false       | true          | false             || ConfigurationRoles.INTENDED_RESOLVABLE
        false       | true          | true              || ConfigurationRoles.INTENDED_RESOLVABLE_BUCKET
        false       | false         | true              || ConfigurationRoles.INTENDED_BUCKET
    }

    def "can not find unknown usage combinations consumable=#consumable, resolvable=#resolvable, declarableAgainst=#declarableAgainst"() {
        expect:
        !ConfigurationRoles.byUsage(consumable, resolvable, declarableAgainst).isPresent()

        where:
        consumable  | resolvable    | declarableAgainst
        false       | false         | false
        true        | true          | false
    }

    def "predefined roles are named"() {
        expect:
        role.getName() == name

        where:
        role                                            || name
        ConfigurationRoles.INTENDED_BUCKET              || "Intended Bucket"
        ConfigurationRoles.INTENDED_CONSUMABLE          || "Intended Consumable"
        ConfigurationRoles.INTENDED_RESOLVABLE          || "Intended Resolvable"
        ConfigurationRoles.INTENDED_RESOLVABLE_BUCKET   || "Intended Resolvable Bucket"
        ConfigurationRoles.LEGACY                       || "Legacy"
    }
}
