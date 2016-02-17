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
import spock.lang.Unroll

class HasPrefixPatternStepTest extends Specification {
    @Unroll
    def "matches name case sensitive when isFile is #isFile"() {
        def step = new HasPrefixPatternStep(".abc", true)

        expect:
        step.matches(".abcd", isFile)
        step.matches(".abc", isFile)
        !step.matches(".", isFile)
        !step.matches(".a", isFile)
        !step.matches(".ab", isFile)
        !step.matches(".b", isFile)
        !step.matches(".bcd", isFile)
        !step.matches("_abc", isFile)
        !step.matches("", isFile)
        !step.matches("something else", isFile)

        where:
        isFile << [true, false]
    }

    @Unroll
    def "matches name case insensitive when isFile is #isFile"() {
        def step = new HasPrefixPatternStep(".abc", false)

        expect:
        step.matches(".abc", isFile)
        step.matches(".ABC", isFile)
        step.matches(".Abc", isFile)
        step.matches(".aBCD", isFile)
        !step.matches(".A", isFile)
        !step.matches(".Ab", isFile)
        !step.matches(".BCD", isFile)
        !step.matches("ABC", isFile)
        !step.matches("", isFile)
        !step.matches("something else", isFile)

        where:
        isFile << [true, false]
    }
}
