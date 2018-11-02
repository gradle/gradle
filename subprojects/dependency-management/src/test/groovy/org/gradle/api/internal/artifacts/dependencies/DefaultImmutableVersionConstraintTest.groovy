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

import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import spock.lang.Specification

class DefaultImmutableVersionConstraintTest extends Specification {
    def "can create an immutable version constraint without rejects"() {
        given:
        def v = new DefaultImmutableVersionConstraint('1.0')

        expect:
        v.requiredVersion == '1.0'
        v.preferredVersion == ''
        v.strictVersion == ''
        v.rejectedVersions == []
    }

    def "can create an immutable version constraint with rejects"() {
        given:
        def v = new DefaultImmutableVersionConstraint('1.1', '1.0', '1.1.1', ['1.2', '2.0'])

        expect:
        v.requiredVersion == '1.0'
        v.preferredVersion == '1.1'
        v.strictVersion == '1.1.1'
        v.rejectedVersions == ['1.2','2.0']
    }

    def "cannot mutate rejection list"() {
        given:
        def v = new DefaultImmutableVersionConstraint('1.1', '1.0', '1.1.1', ['1.2', '2.0'])

        when:
        v.rejectedVersions.add('3.0')

        then:
        def e = thrown(UnsupportedOperationException)
    }

    def "cannot use null as any version"() {
        when:
        new DefaultImmutableVersionConstraint('1.1', null, '1.1', ['1.2', '2.0'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Required version must not be null'

        when:
        new DefaultImmutableVersionConstraint(null, '1.0', '1.0', ['1.2', '2.0'])

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Preferred version must not be null'

        when:
        new DefaultImmutableVersionConstraint('1.0', '1.0', null, ['1.2', '2.0'])

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Strict version must not be null'
    }

    def "cannot use empty or null as rejected version"() {

        when:
        new DefaultImmutableVersionConstraint('', '', '', [null])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Rejected version must not be empty'

        when:
        new DefaultImmutableVersionConstraint('', '', '', [''])

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Rejected version must not be empty'
    }

    def "can use empty as preferred and strict version"() {
        when:
        def v = new DefaultImmutableVersionConstraint('')

        then:
        v.preferredVersion == ''
        v.strictVersion == ''
        v.rejectedVersions.empty

        when:
        v = new DefaultImmutableVersionConstraint('', '', '', ['1.1', '2.0'])

        then:
        v.preferredVersion == ''
        v.strictVersion == ''
        v.rejectedVersions == ['1.1', '2.0']
    }

    def "cannot use null as rejected versions"() {
        when:
        def v = new DefaultImmutableVersionConstraint('', '1.0', '', null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Rejected versions must not be null'
    }

    def "doesn't create a copy of an already immutable version constraint"() {
        given:
        def v = new DefaultImmutableVersionConstraint('1.0')

        when:
        def c = DefaultImmutableVersionConstraint.of(v)

        then:
        v.is(c)
    }

    def "can convert mutable version constraint to immutable version constraint"() {
        given:
        def v = new DefaultMutableVersionConstraint('1.0', '2.0', '3.0', ['1.1', '2.0'])

        when:
        def c = DefaultImmutableVersionConstraint.of(v)

        then:
        !v.is(c)
        c instanceof ImmutableVersionConstraint
        c.requiredVersion == v.requiredVersion
        c.preferredVersion == v.preferredVersion
        c.strictVersion == v.strictVersion
        c.rejectedVersions == v.rejectedVersions
    }


    def "has useful displayName"() {
        expect:
        displayNameFor('1.0', '', '', []) == "{prefer 1.0}"
        displayNameFor('', '1.0', '', []) == "1.0"
        displayNameFor('', '', '1.0', []) == "{strictly 1.0}"
        displayNameFor('', '', '', ['1.0', '2.0']) == "{reject 1.0 & 2.0}"

        displayNameFor('1.0', '2.0', '3.0', ['1.0', '2.0']) == "{strictly 3.0; require 2.0; prefer 1.0; reject 1.0 & 2.0}"
        displayNameFor('1.0', '', '', [], 'br') == "{prefer 1.0; branch br}"

        displayNameFor('1.0', '1.0', '', []) == "1.0" // prefer == require
        displayNameFor('', '1.0', '1.0', []) == "{strictly 1.0}" // strictly == require
        displayNameFor('1.0', '', '1.0', []) == "{strictly 1.0}" // strictly == prefer
        displayNameFor('1.0', '1.0', '1.0', []) == "{strictly 1.0}" // strictly == prefer == require
    }

    private String displayNameFor(String preferred, String required, String strict, List<String> rejects, branch = null) {
        new DefaultImmutableVersionConstraint(preferred, required, strict, rejects, branch).displayName
    }

}
