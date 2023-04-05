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

import static org.gradle.api.internal.artifacts.configurations.ConfigurationRole.RoleDescriber

class RoleDescriberSpec extends Specification {
    def "can describe usage for role"() {
        given:
        def role = ConfigurationRolesForMigration.INTENDED_RESOLVABLE_BUCKET_TO_INTENDED_RESOLVABLE

        expect:
        RoleDescriber.describeRole(role) == "\tResolvable - this configuration can be resolved by this project to a set of files\n" +
                "\tDeclarable Against - this configuration can have dependencies added to it (but this behavior is marked deprecated)"
    }

    def "can describe usage for role which allows nothing"() {
        given:
        def role = new ConfigurationRole() {
            @Override
            String getName() {
                return "test"
            }

            @Override
            boolean isConsumable() {
                return false
            }

            @Override
            boolean isResolvable() {
                return false
            }

            @Override
            boolean isDeclarableAgainst() {
                return false
            }

            @Override
            boolean isConsumptionDeprecated() {
                return false
            }

            @Override
            boolean isResolutionDeprecated() {
                return false
            }

            @Override
            boolean isDeclarationAgainstDeprecated() {
                return false
            }
        }

        expect:
        RoleDescriber.describeRole(role) == "\tThis configuration does not allow any usage"
    }
}
