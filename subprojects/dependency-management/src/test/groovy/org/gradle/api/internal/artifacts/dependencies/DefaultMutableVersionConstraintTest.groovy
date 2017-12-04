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

import org.gradle.api.InvalidUserDataException
import spock.lang.Specification
import spock.lang.Unroll

class DefaultMutableVersionConstraintTest extends Specification {
    def "defaults to an empty reject list"() {
        when:
        def e = new DefaultMutableVersionConstraint('1.0')

        then:
        e.preferredVersion == '1.0'
        e.rejectedVersions == []
    }

    @Unroll
    def "computes the complement of preferred version #preferred"() {
        when:
        def e = new DefaultMutableVersionConstraint(preferred, true)

        then:
        e.preferredVersion == preferred
        e.rejectedVersions == [complement]

        where:
        preferred    | complement
        '1.0'        | ']1.0,)'
        '[1.0, 2.0]' | ']2.0,)'
        '[1.0, 2.0[' | '[2.0,)'
        '(, 2.0['    | '[2.0,)'
        '(, 2.0]'    | ']2.0,)'
    }

    @Unroll
    def "fails converting version #preferred to a strict dependency"() {
        when:
        def e = new DefaultMutableVersionConstraint(preferred, true)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "Version '$preferred' cannot be converted to a strict version constraint."

        where:
        preferred << ['[1.0,)', '1.+', '1+']
    }

    def "can override preferred version with another preferred version"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')

        when:
        version.prefer('2.0')

        then:
        version.preferredVersion == '2.0'
        version.rejectedVersions == []
    }

    def "can override strict version with preferred version"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0', true)

        when:
        version.prefer('2.0')

        then:
        version.preferredVersion == '2.0'
        version.rejectedVersions == []
    }

    def "can override preferred version with strict version"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')

        when:
        version.strictly('2.0')

        then:
        version.preferredVersion == '2.0'
        version.rejectedVersions == [']2.0,)']
    }

    def "can declare rejected versions"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')

        when:
        version.reject('1.0.1')

        then:
        version.preferredVersion == '1.0'
        version.rejectedVersions == ['1.0.1']

        when:
        version.reject('1.0.2')

        then:
        version.preferredVersion == '1.0'
        version.rejectedVersions == ['1.0.1', '1.0.2']
    }

    def "calling 'prefers' resets the list of rejects"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')
        version.reject('1.0.1')

        when:
        version.prefer('1.1')

        then:
        version.preferredVersion == '1.1'
        version.rejectedVersions == []
    }

    def "calling 'strictly' resets the list of rejects"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')
        version.reject('1.0.1')

        when:
        version.strictly('1.1')

        then:
        version.preferredVersion == '1.1'
        version.rejectedVersions == [']1.1,)']
    }

    def "cannot use an empty list of rejections"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')

        when:
        version.reject()

        then:
        InvalidUserDataException e = thrown()
        e.message == "The 'reject' clause requires at least one rejected version"
    }

    def "calling rejectAll is equivalent to having empty preferred version and '+' reject"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')
        version.reject('1.1', '1.2')

        when:
        version.rejectAll()

        then:
        version.preferredVersion == ''
        version.getRejectedVersions() == ['+']
    }

}
