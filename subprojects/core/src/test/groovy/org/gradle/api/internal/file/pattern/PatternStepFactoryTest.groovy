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

package org.gradle.api.internal.file.pattern

import spock.lang.Specification

class PatternStepFactoryTest extends Specification {
    def "creates step for ** wildcard"() {
        expect:
        def step = PatternStepFactory.getStep("**", true, true);
        step instanceof AnyWildcardPatternStep
        step.matches("anything", true)
        step.matches("anything", false)
        step.matches("", true)
        step.matches("", false)
    }

    def "creates step for * wildcard"() {
        expect:
        def step = PatternStepFactory.getStep("*", true, true);
        step instanceof AnyWildcardPatternStep
        step.matches("anything", true)
        !step.matches("anything", false)
        step.matches("", true)
        !step.matches("", false)
    }

    def "creates step for * prefix wildcard"() {
        expect:
        def step1 = PatternStepFactory.getStep("*abc", true, true);
        step1 instanceof HasSuffixPatternStep
        step1.suffix == "abc"

        step1.matches("abc", true)
        step1.matches("abc", false)
        step1.matches("thing.abc", true)
        step1.matches("thing.abc", false)
        !step1.matches("thing.java", true)
        !step1.matches("thing.java", false)

        and:
        def step2 = PatternStepFactory.getStep("**abc", true, true);
        step2 instanceof HasSuffixPatternStep
        step2.suffix == "abc"

        step2.matches("abc", true)
        step2.matches("abc", false)
        step2.matches("thing.abc", true)
        step2.matches("thing.abc", false)
        !step2.matches("thing.java", true)
        !step2.matches("thing.java", false)
    }

    def "creates step for * suffix wildcard"() {
        expect:
        def step1 = PatternStepFactory.getStep("abc*", true, true);
        step1 instanceof HasPrefixPatternStep
        step1.prefix == "abc"

        step1.matches("abc", true)
        step1.matches("abc.java", true)
        !step1.matches("ab", true)

        and:
        def step2 = PatternStepFactory.getStep("abc**", true, true);
        step2 instanceof HasPrefixPatternStep
        step2.prefix == "abc"

        step2.matches("abc", true)
        step2.matches("abc.java", true)
        !step2.matches("ab", true)
    }

    def "creates step for * suffix and prefix wildcard"() {
        expect:
        def step1 = PatternStepFactory.getStep("a*c", true, true);
        step1 instanceof HasPrefixAndSuffixPatternStep

        step1.matches("ac", true)
        step1.matches("abac", true)
        !step1.matches("bc", true)
        !step1.matches("ab", true)

        and:
        def step2 = PatternStepFactory.getStep("a**c", true, true);
        step2 instanceof HasPrefixAndSuffixPatternStep

        step1.matches("ac", true)
        step1.matches("abac", true)
        !step1.matches("bc", true)
        !step1.matches("ab", true)
    }

    def "creates step for wildcard segment"() {
        expect:
        def step1 = PatternStepFactory.getStep("a?c", true, true);
        step1 instanceof RegExpPatternStep
        step1.matches("abc", true)
        !step1.matches("ABC", true)
        !step1.matches("other", true)

        and:
        def step2 = PatternStepFactory.getStep("a*?c", true, true);
        step2 instanceof RegExpPatternStep
        step2.matches("abc", true)
        step2.matches("abac", true)
        !step2.matches("ac", true)
        !step2.matches("ABC", true)
        !step2.matches("other", true)

        and:
        def step3 = PatternStepFactory.getStep("?bc", true, true);
        step3 instanceof RegExpPatternStep
        step3.matches("abc", true)
        step3.matches("Abc", true)
        !step3.matches("bc", true)
        !step3.matches("ABC",true)
        !step3.matches("other", true)

        and:
        def step4 = PatternStepFactory.getStep("*?bc", true, true);
        step4 instanceof RegExpPatternStep
        step4.matches("abc", true)
        step4.matches("123abc", true)
        !step4.matches("bc",  true)
        !step4.matches("ABC", true)
        !step4.matches("other",  true)

        and:
        def step5 = PatternStepFactory.getStep("?*bc", true, true);
        step5 instanceof RegExpPatternStep
        step5.matches("abc", true)
        step5.matches("123abc", true)
        !step5.matches("bc", true)
        !step5.matches("ABC", true)
        !step5.matches("other", true)

        and:
        def step6 = PatternStepFactory.getStep("*bc*?", true, true);
        step6 instanceof RegExpPatternStep
        step6.matches("bcd", true)
        step6.matches("abcd", true)
        step6.matches("123abc1", true)
        !step6.matches("bc", true)
        !step6.matches("BC", true)
        !step6.matches("ABC", true)
        !step6.matches("other", true)

        and:
        def step7 = PatternStepFactory.getStep("?", true, true);
        step7 instanceof RegExpPatternStep
        step7.matches("a", true)
        !step7.matches("", true)
        !step7.matches("abc", true)

        and:
        def step8 = PatternStepFactory.getStep("*a*b*c*", true, true);
        step8 instanceof RegExpPatternStep
        step8.matches("abc", true)
        step8.matches("1a2b3c4", true)
        !step8.matches("", true)
        !step8.matches("ab", true)
        !step8.matches("1a2b", true)
    }

    def "creates step for non-wildcard segment"() {
        expect:
        def step = PatternStepFactory.getStep("abc", true, true);
        step instanceof FixedPatternStep
        step.value == "abc"

        step.matches("abc", true)
        !step.matches("ABC", true)
        !step.matches("other", true)
    }
}
