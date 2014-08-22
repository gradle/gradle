/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import spock.lang.Specification
import org.gradle.api.internal.artifacts.ComponentSelectionInternal.State

class DefaultComponentSelectionTest extends Specification {
    DefaultComponentSelection selection

    def setup() {
        selection = new DefaultComponentSelection(Stub(DependencyMetaData), Stub(ModuleComponentIdentifier))
    }

    def "default state of version selection is not set" () {
        expect:
        selection.state == State.NOT_SET
    }

    def "accepting or rejection changes state correctly" () {
        when:
        selection."${operation}"()

        then:
        selection.state == expectedState

        where:
        operation | expectedState
        "accept"  | State.ACCEPTED
        "reject"  | State.REJECTED
    }

    def "changing an already accepted or rejected throws an exception"() {
        when:
        operations.each { operation ->
            selection."${operation}"()
        }

        then:
        def e = thrown(InvalidUserCodeException)

        where:
        operations            | _
        [ "accept", "reject"] | _
        [ "reject", "accept"] | _
    }

    def "accepting or rejecting multiple times does not throw an exception"() {
        when:
        (1..3).each {
            selection."${operation}"()
        }

        then:
        noExceptionThrown()
        selection.state == expectedState

        where:
        operation | expectedState
        "accept"  | State.ACCEPTED
        "reject"  | State.REJECTED
    }
}
