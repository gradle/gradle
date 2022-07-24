/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.artifacts.DependencyConstraint
import spock.lang.Specification

class DefaultDependencyConstraintTest extends Specification {

    private DependencyConstraint dependencyConstraint = new DefaultDependencyConstraint("org.gradle", "gradle-core", "4.4-beta2")

    void "has reasonable default values"() {
        expect:
        dependencyConstraint.group == "org.gradle"
        dependencyConstraint.name == "gradle-core"
        dependencyConstraint.version == "4.4-beta2"
        dependencyConstraint.versionConstraint.preferredVersion == ""
        dependencyConstraint.versionConstraint.requiredVersion == "4.4-beta2"
        dependencyConstraint.versionConstraint.strictVersion == ""
        dependencyConstraint.versionConstraint.rejectedVersions == []
    }


    void "knows if is equal to"() {
        expect:
        constraint("group1", "name1", "version1") == constraint("group1", "name1", "version1")
        constraint("group1", "name1", "version1").hashCode() == constraint("group1", "name1", "version1").hashCode()
        constraint("group1", "name1", "version1") != constraint("group1", "name1", "version2")
        constraint("group1", "name1", "version1") != constraint("group1", "name2", "version1")
        constraint("group1", "name1", "version1") != constraint("group2", "name1", "version1")
        constraint("group1", "name1", "version1") != constraint("group2", "name1", "version1")
    }

    DefaultDependencyConstraint constraint(String group, String name, String version) {
        return new DefaultDependencyConstraint(group, name, version)
    }

    def "creates a strict version"() {
        when:
        def constraint = DefaultDependencyConstraint.strictly("org", "foo", "1.0")

        then:
        constraint.versionConstraint.strictVersion == '1.0'
    }

    def "creates deep copy"() {
        when:
        def copy = dependencyConstraint.copy() as DependencyConstraint

        then:
        assertDeepCopy(dependencyConstraint, copy)
    }

    static void assertDeepCopy(DependencyConstraint dependencyConstraint, DependencyConstraint copiedDependencyConstraint) {
        assert copiedDependencyConstraint.group == dependencyConstraint.group
        assert copiedDependencyConstraint.name == dependencyConstraint.name
        assert copiedDependencyConstraint.version == dependencyConstraint.version
        assert copiedDependencyConstraint.versionConstraint == copiedDependencyConstraint.versionConstraint
        assert copiedDependencyConstraint.versionConstraint.preferredVersion == copiedDependencyConstraint.versionConstraint.preferredVersion
        assert copiedDependencyConstraint.versionConstraint.rejectedVersions == copiedDependencyConstraint.versionConstraint.rejectedVersions
    }
}
