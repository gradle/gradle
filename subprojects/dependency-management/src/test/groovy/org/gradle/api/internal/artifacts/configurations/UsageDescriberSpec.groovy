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

class UsageDescriberSpec extends Specification {
    def "can describe usage for role"() {
        given:
        def role = ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_RESOLVABLE

        expect:
        UsageDescriber.describeRole(role) == "\tResolvable - this configuration can be resolved by this project to a set of files\n" +
                "\tDeclarable - this configuration can have dependencies added to it (but this behavior is marked deprecated)"
    }

    def "can describe usage for role which allows nothing"() {
        given:
        def role = new DefaultConfigurationRole("test", false, false, false, false, false, false)

        expect:
        UsageDescriber.describeRole(role) == "\tThis configuration does not allow any usage"
    }
}
