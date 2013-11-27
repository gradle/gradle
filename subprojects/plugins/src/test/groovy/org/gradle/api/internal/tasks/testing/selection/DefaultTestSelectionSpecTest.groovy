/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.selection

import org.gradle.api.InvalidUserDataException
import spock.lang.Specification

class DefaultTestSelectionSpecTest extends Specification {

    def spec = new DefaultTestSelectionSpec()

    def "allows configuring test names"() {
        expect: spec.names.isEmpty()

        when:
        spec.name("*fooMethod")
        spec.name("*.FooTest.*")

        then: spec.names == ["*fooMethod", "*.FooTest.*"] as Set

        when: spec.setNames("x")

        then: spec.names == ["x"] as Set
    }

    def "prevents empty names"() {
        when: spec.name(null)
        then: thrown(InvalidUserDataException)

        when: spec.name("")
        then: thrown(InvalidUserDataException)

        when: spec.setNames("ok", "")
        then: thrown(InvalidUserDataException)
    }
}
