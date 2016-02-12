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

class HasPrefixAndSuffixPatternStepTest extends Specification {
    @Unroll
    def "matches name case sensitive when isFile is #isFile"() {
        def step = new HasPrefixAndSuffixPatternStep("pre", "suf", true)

        expect:
        step.matches("pre-suf", isFile)
        step.matches("presufsuf", isFile)
        step.matches("presuf", isFile)
        !step.matches("", isFile)
        !step.matches("pre", isFile)
        !step.matches("pre-su", isFile)
        !step.matches("pre-s", isFile)
        !step.matches("suf", isFile)
        !step.matches("re-suf", isFile)
        !step.matches("e-suf", isFile)
        !step.matches("Pre-Suf", isFile)
        !step.matches("", isFile)
        !step.matches("something else", isFile)

        where:
        isFile << [true, false]
    }

    @Unroll
    def "matches name case insensitive when isFile is #isFile"() {
        def step = new HasPrefixAndSuffixPatternStep("pre", "suf", false)

        expect:
        step.matches("pre-suf", isFile)
        step.matches("PRE-SUF", isFile)
        step.matches("Pre-Suf", isFile)
        !step.matches("PRE", isFile)
        !step.matches("SUF", isFile)
        !step.matches("PRSU", isFile)
        !step.matches("", isFile)
        !step.matches("something else", isFile)

        where:
        isFile << [true, false]
    }
}
