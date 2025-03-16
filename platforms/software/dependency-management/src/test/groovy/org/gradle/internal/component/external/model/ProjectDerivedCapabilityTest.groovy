/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.external.model

import org.gradle.api.Project
import org.gradle.api.capabilities.Capability
import spock.lang.Specification

class ProjectDerivedCapabilityTest extends Specification {

    def "equals and hash code are compatible with immutable capability"() {
        Capability capability1 = new ProjectDerivedCapability(project("org", "name", "1.0"))
        Capability capability2 = new DefaultImmutableCapability("org", "name", "1.0")

        expect:
        hashCodeAndEqualsAreCompatible(capability1, capability2)
    }

    def "equals and hash code are compatible with immutable capability when using feature name"() {
        Capability capability1 = new ProjectDerivedCapability(project("org", "name", "1.0"), "featureName")
        Capability capability2 = new DefaultImmutableCapability("org", "name-feature-name", "1.0")

        expect:
        hashCodeAndEqualsAreCompatible(capability1, capability2)
    }

    boolean hashCodeAndEqualsAreCompatible(Capability capability1, Capability capability2) {
        return capability1 == capability2 && capability1.hashCode() == capability2.hashCode()
    }

    Project project(String group, String name, String version) {
        Mock(Project) {
            getGroup() >> group
            getName() >> name
            getVersion() >> version
        }
    }
}
