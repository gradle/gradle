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


import spock.lang.Specification

class DefaultMutableVersionConstraintTest extends Specification {
    def "defaults to an empty reject list"() {
        when:
        def e = new DefaultMutableVersionConstraint('1.0')

        then:
        e.requiredVersion == '1.0'
        e.preferredVersion == ''
        e.strictVersion == ''
        e.rejectedVersions == []
    }

    def "can override preferred version"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')
        version.prefer('2.0')

        when:
        version.prefer('3.0')

        then:
        version.requiredVersion == '1.0'
        version.preferredVersion == '3.0'
        version.strictVersion == ''
        version.rejectedVersions == []
    }

    def "can override required version"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')
        version.prefer('2.0')

        when:
        version.require('3.0')

        then:
        version.requiredVersion == '3.0'
        version.preferredVersion == '2.0'
        version.strictVersion == ''
        version.rejectedVersions == []
    }

    def "can override required version with strict version"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')

        when:
        version.strictly('2.0')

        then:
        version.requiredVersion == '2.0'
        version.preferredVersion == ''
        version.strictVersion == '2.0'
    }

    def "can override strict version with required version"() {
        given:
        def version = DefaultMutableVersionConstraint.withStrictVersion('1.0')

        when:
        version.require('2.0')

        then:
        version.requiredVersion == '2.0'
        version.preferredVersion == ''
        version.strictVersion == ''
    }

    def "can combine strict version with preferred version"() {
        given:
        def version = DefaultMutableVersionConstraint.withStrictVersion('1.0')

        when:
        version.prefer('2.0')

        then:
        version.requiredVersion == '1.0'
        version.preferredVersion == '2.0'
        version.strictVersion == '1.0'
        version.rejectedVersions == []
    }

    def "can declare rejected versions"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')

        when:
        version.reject('1.0.1')

        then:
        version.requiredVersion == '1.0'
        version.rejectedVersions == ['1.0.1']

        when:
        version.reject('1.0.1', '1.0.2')

        then:
        version.requiredVersion == '1.0'
        version.rejectedVersions == ['1.0.1', '1.0.2']
    }

    def "calling 'prefers' resets the list of rejects"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')
        version.reject('1.0.1')

        when:
        version.prefer('1.1')

        then:
        version.requiredVersion == '1.0'
        version.preferredVersion == '1.1'
        version.strictVersion == ''
        version.rejectedVersions == []
    }

    def "calling 'require' resets the list of rejects"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')
        version.reject('1.0.1')

        when:
        version.require('1.1')

        then:
        version.requiredVersion == '1.1'
        version.preferredVersion == ''
        version.strictVersion == ''
        version.rejectedVersions == []
    }

    def "calling 'strictly' resets the list of rejects"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')
        version.reject('1.0.1')

        when:
        version.strictly('1.1')

        then:
        version.requiredVersion == '1.1'
        version.preferredVersion == ''
        version.strictVersion == '1.1'
        version.rejectedVersions == []
    }

    def "can clear list of rejections"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')
        version.reject("1.1")

        when:
        version.reject()

        then:
        version.rejectedVersions == []
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


    def "strict version does not clear preferred version"() {
        given:
        def version = new DefaultMutableVersionConstraint('1.0')

        when:
        version.prefer('1.5')
        version.strictly('[1,2)')

        then:
        version.requiredVersion == '[1,2)'
        version.preferredVersion == '1.5'
        version.strictVersion == '[1,2)'
    }
}
