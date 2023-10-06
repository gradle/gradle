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

import spock.lang.Issue
import spock.lang.Specification

class HasPrefixAndSuffixPatternStepTest extends Specification {
    static final boolean CASE_SENSITIVE = true;
    static final boolean CASE_INSENSITIVE = false;

    def "matches name case sensitive"() {
        def step = new HasPrefixAndSuffixPatternStep("pre", "suf", CASE_SENSITIVE)

        expect:
        step.matches("pre-suf")
        step.matches("presufsuf")
        step.matches("presuf")
        !step.matches("")
        !step.matches("pre")
        !step.matches("pre-su")
        !step.matches("pre-s")
        !step.matches("suf")
        !step.matches("re-suf")
        !step.matches("e-suf")
        !step.matches("Pre-Suf")
        !step.matches("")
        !step.matches("something else")
    }

    def "matches name case insensitive"() {
        def step = new HasPrefixAndSuffixPatternStep("pre", "suf", CASE_INSENSITIVE)

        expect:
        step.matches("pre-suf")
        step.matches("PRE-SUF")
        step.matches("Pre-Suf")
        !step.matches("PRE")
        !step.matches("SUF")
        !step.matches("PRSU")
        !step.matches("")
        !step.matches("something else")
    }


    @Issue("GRADLE-3418")
    def "doesn't match if pre and suf are the same"() {
        def step = new HasPrefixAndSuffixPatternStep("pat", "pat", CASE_SENSITIVE)

        expect:
        step.matches("patpat")
        step.matches("pat-pat")
        !step.matches("")
        !step.matches("pat")
    }
}
