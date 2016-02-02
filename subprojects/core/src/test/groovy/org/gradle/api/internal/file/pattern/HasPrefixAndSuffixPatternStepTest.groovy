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

class HasPrefixAndSuffixPatternStepTest extends Specification {
    def "matches name case sensitive"() {
        def step = new HasPrefixAndSuffixPatternStep("pre", "suf", true)

        expect:
        step.matches("pre-suf", true)
        step.matches("presufsuf", true)
        step.matches("presuf", true)
        !step.matches("", true)
        !step.matches("pre", true)
        !step.matches("pre-su", true)
        !step.matches("pre-s", true)
        !step.matches("suf", true)
        !step.matches("re-suf", true)
        !step.matches("e-suf", true)
        !step.matches("Pre-Suf", true)
        !step.matches("", true)
        !step.matches("something else", true)
    }

    def "matches name case insensitive"() {
        def step = new HasPrefixAndSuffixPatternStep("pre", "suf", false)

        expect:
        step.matches("pre-suf", true)
        step.matches("PRE-SUF", true)
        step.matches("Pre-Suf", true)
        !step.matches("PRE", true)
        !step.matches("SUF", true)
        !step.matches("PRSU", true)
        !step.matches("", true)
        !step.matches("something else", true)
    }
}
