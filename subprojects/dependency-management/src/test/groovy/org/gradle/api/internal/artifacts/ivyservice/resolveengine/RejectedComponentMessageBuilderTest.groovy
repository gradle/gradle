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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine

class RejectedComponentMessageBuilderTest extends AbstractConflictResolverTest {

    def "builds message for rejected version"() {
        when:
        prefer('1.2')
        strictly('1.1')

        then:
        failureMessage == """Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':root:' --> 'org:foo' prefers '1.2'
   Dependency path ':root:' --> 'org:foo' prefers '1.1', rejects ']1.1,)'
"""
    }

    def "reasonable error message when path to dependency isn't simple"() {
        when:
        prefer('1.2', module('org', 'bar', '1.0', module('org', 'baz', '1.0')))
        strictly('1.1', module('com', 'other', '15'))

        then:
        failureMessage == """Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':root:' --> 'org:baz:1.0' --> 'org:bar:1.0' --> 'org:foo' prefers '1.2'
   Dependency path ':root:' --> 'com:other:15' --> 'org:foo' prefers '1.1', rejects ']1.1,)'
"""
    }

    def "recognizes a rejectAll clause"() {
        when:
        prefer('1.2', module('org', 'bar', '1.0', module('org', 'baz', '1.0')))
        participants << module('org', 'foo', '', module('com', 'other', '15')).rejectAll()

        then:
        failureMessage == """Module 'org:foo' has been rejected:
   Dependency path ':root:' --> 'org:baz:1.0' --> 'org:bar:1.0' --> 'org:foo' prefers '1.2'
   Dependency path ':root:' --> 'com:other:15' --> 'org:foo' rejects all versions
"""
    }

    private String getFailureMessage() {
        new RejectedComponentMessageBuilder().buildFailureMessage(participants)
    }

}
