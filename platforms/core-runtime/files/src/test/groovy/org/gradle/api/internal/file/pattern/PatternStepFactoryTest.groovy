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
        def step = PatternStepFactory.getStep("**", true);
        step instanceof AnyWildcardPatternStep
        step.matches("anything")
        step.matches("")
    }

    def "creates step for * wildcard"() {
        expect:
        def step = PatternStepFactory.getStep("*", true);
        step instanceof AnyWildcardPatternStep
        step.matches("anything")
        step.matches("")
    }

    def "creates step for * prefix wildcard"() {
        expect:
        def step1 = PatternStepFactory.getStep("*abc", true);
        step1 instanceof HasSuffixPatternStep
        step1.suffix == "abc"

        step1.matches("abc")
        step1.matches("thing.abc")
        !step1.matches("thing.java")

        and:
        def step2 = PatternStepFactory.getStep("**abc", true);
        step2 instanceof HasSuffixPatternStep
        step2.suffix == "abc"

        step2.matches("abc")
        step2.matches("thing.abc")
        !step2.matches("thing.java")
    }

    def "creates step for * suffix wildcard"() {
        expect:
        def step1 = PatternStepFactory.getStep("abc*", true);
        step1 instanceof HasPrefixPatternStep
        step1.prefix == "abc"

        step1.matches("abc")
        step1.matches("abc.java")
        !step1.matches("ab")

        and:
        def step2 = PatternStepFactory.getStep("abc**", true);
        step2 instanceof HasPrefixPatternStep
        step2.prefix == "abc"

        step2.matches("abc")
        step2.matches("abc.java")
        !step2.matches("ab")
    }

    def "creates step for * suffix and prefix wildcard"() {
        expect:
        def step1 = PatternStepFactory.getStep("a*c", true);
        step1 instanceof HasPrefixAndSuffixPatternStep

        step1.matches("ac")
        step1.matches("abac")
        !step1.matches("bc")
        !step1.matches("ab")
        !step1.matches("a")
        !step1.matches("c")

        and:
        def step2 = PatternStepFactory.getStep("a**c", true);
        step2 instanceof HasPrefixAndSuffixPatternStep

        step2.matches("ac")
        step2.matches("abac")
        !step2.matches("bc")
        !step2.matches("ab")
    }

    def "creates step for wildcard segment"() {
        expect:
        def step1 = PatternStepFactory.getStep("a?c", true);
        step1 instanceof RegExpPatternStep
        step1.matches("abc")
        !step1.matches("ABC")
        !step1.matches("other")

        and:
        def step2 = PatternStepFactory.getStep("a*?c", true);
        step2 instanceof RegExpPatternStep
        step2.matches("abc")
        step2.matches("abac")
        !step2.matches("ac")
        !step2.matches("ABC")
        !step2.matches("other")

        and:
        def step3 = PatternStepFactory.getStep("?bc", true);
        step3 instanceof RegExpPatternStep
        step3.matches("abc")
        step3.matches("Abc")
        !step3.matches("bc")
        !step3.matches("ABC")
        !step3.matches("other")

        and:
        def step4 = PatternStepFactory.getStep("*?bc", true);
        step4 instanceof RegExpPatternStep
        step4.matches("abc")
        step4.matches("123abc")
        !step4.matches("bc")
        !step4.matches("ABC")
        !step4.matches("other")

        and:
        def step5 = PatternStepFactory.getStep("?*bc", true);
        step5 instanceof RegExpPatternStep
        step5.matches("abc")
        step5.matches("123abc")
        !step5.matches("bc")
        !step5.matches("ABC")
        !step5.matches("other")

        and:
        def step6 = PatternStepFactory.getStep("*bc*?", true);
        step6 instanceof RegExpPatternStep
        step6.matches("bcd")
        step6.matches("abcd")
        step6.matches("123abc1")
        !step6.matches("bc")
        !step6.matches("BC")
        !step6.matches("ABC")
        !step6.matches("other")

        and:
        def step7 = PatternStepFactory.getStep("?", true);
        step7 instanceof RegExpPatternStep
        step7.matches("a")
        !step7.matches("")
        !step7.matches("abc")

        and:
        def step8 = PatternStepFactory.getStep("*a*b*c*", true);
        step8 instanceof RegExpPatternStep
        step8.matches("abc")
        step8.matches("1a2b3c4")
        !step8.matches("")
        !step8.matches("ab")
        !step8.matches("1a2b")
    }

    def "creates step for non-wildcard segment"() {
        expect:
        def step = PatternStepFactory.getStep("abc", true);
        step instanceof FixedPatternStep
        step.value == "abc"

        step.matches("abc")
        !step.matches("ABC")
        !step.matches("other")
    }
}
