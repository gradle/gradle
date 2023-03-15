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

import static org.gradle.api.internal.artifacts.configurations.ConfigurationRole.RoleDescriber.*

class ConfigurationRoleSpec extends Specification {
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
    }

    private void assertDescriptionContains(ConfigurationRole role, List<String> usages) {
        for (String usage : usages) {
            assert role.describeUsage().contains(usage)
        }
    }
}
