/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.locking

import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification
import spock.lang.Unroll

class DependencyLockingNotationConverterTest extends Specification {

    def 'converts a lock notation to a strict dependency constraint when no update mode'() {
        given:
        def converter = new DependencyLockingNotationConverter(false)
        def lockEntry = 'org:foo:1.1'

        when:
        def converted = converter.convertToDependencyConstraint(lockEntry)

        then:
        converted instanceof DependencyConstraint
        converted.group == 'org'
        converted.name == 'foo'
        converted.version == '1.1'
        converted.reason == 'dependency was locked to version \'1.1\''
        converted.versionConstraint.preferredVersion == '1.1'
        converted.versionConstraint.rejectedVersions == [']1.1,)']
    }

    def 'converts a lock notation to a prefer dependency constraint in update mode'() {
        given:
        def converter = new DependencyLockingNotationConverter(true)
        def lockEntry = 'org:foo:1.1'

        when:
        def converted = converter.convertToDependencyConstraint(lockEntry)

        then:
        converted instanceof DependencyConstraint
        converted.group == 'org'
        converted.name == 'foo'
        converted.version == '1.1'
        converted.reason == 'dependency was locked to version \'1.1\' (update mode)'
        converted.versionConstraint.preferredVersion == '1.1'
        converted.versionConstraint.rejectedVersions.isEmpty()
    }

    @Unroll
    def "fails to convert an invalid lock notation: #lockEntry"() {
        when:
        def converter = new DependencyLockingNotationConverter(false)
        def converted = converter.convertToDependencyConstraint(lockEntry)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "The module notation does not respect the lock file format of 'group:name:version' - received '$lockEntry'"

        where:
        lockEntry << ['invalid', 'invalid:invalid']
    }

    def 'converts a ModuleComponentIdentifier to a lock notation'() {
        given:
        def converter = new DependencyLockingNotationConverter(false)
        def module = new DefaultModuleComponentIdentifier('org', 'foo', '1.1')

        when:
        def converted = converter.convertToLockNotation(module)

        then:
        converted == 'org:foo:1.1'
    }
}
