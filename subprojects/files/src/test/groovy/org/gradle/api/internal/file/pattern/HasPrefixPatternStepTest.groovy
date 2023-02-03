/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.file.pattern

import spock.lang.Specification

class HasPrefixPatternStepTest extends Specification {
    def "matches name case sensitive"() {
        def step = new HasPrefixPatternStep(".abc", true)

        expect:
        step.matches(".abcd")
        step.matches(".abc")
        !step.matches(".")
        !step.matches(".a")
        !step.matches(".ab")
        !step.matches(".b")
        !step.matches(".bcd")
        !step.matches("_abc")
        !step.matches("")
        !step.matches("something else")
    }

    def "matches name case insensitive"() {
        def step = new HasPrefixPatternStep(".abc", false)

        expect:
        step.matches(".abc")
        step.matches(".ABC")
        step.matches(".Abc")
        step.matches(".aBCD")
        !step.matches(".A")
        !step.matches(".Ab")
        !step.matches(".BCD")
        !step.matches("ABC")
        !step.matches("")
        !step.matches("something else")
    }
}
