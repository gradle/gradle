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
        step.matches(".abcd", true)
        step.matches(".abc", true)
        !step.matches(".", true)
        !step.matches(".a", true)
        !step.matches(".ab", true)
        !step.matches(".b", true)
        !step.matches(".bcd", true)
        !step.matches("_abc", true)
        !step.matches("", true)
        !step.matches("something else", true)
    }

    def "matches name case insensitive"() {
        def step = new HasPrefixPatternStep(".abc", false)

        expect:
        step.matches(".abc", true)
        step.matches(".ABC", true)
        step.matches(".Abc", true)
        step.matches(".aBCD", true)
        !step.matches(".A", true)
        !step.matches(".Ab", true)
        !step.matches(".BCD", true)
        !step.matches("ABC", true)
        !step.matches("", true)
        !step.matches("something else", true)
    }
}
