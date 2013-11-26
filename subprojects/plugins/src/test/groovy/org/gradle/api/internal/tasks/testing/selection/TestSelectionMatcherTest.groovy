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

import spock.lang.Specification

class TestSelectionMatcherTest extends Specification {

    private static TestSelectionMatcher matcher(String classPattern, String methodPattern) {
        new TestSelectionMatcher(new DefaultTestSelectionSpec(classPattern, methodPattern))
    }

    def "knows if test matches"() {
        def m = matcher("foo*", "*bar")

        expect:
        m.matchesTest("fooxxx", "xxxbar")
        m.matchesTest("foo", "bar")

        !m.matchesTest("com.fooxxx", "xxxbar")
        !m.matchesTest("fooxxx", "bar.foo")
    }

    def "knows if class matches"() {
        def m = matcher("foo*", "*bar")

        expect:
        m.matchesClass("foo")
        m.matchesClass("fooTest")

        !m.matchesClass("com.foo")
    }

    def "regexp is escaped"() {
        expect:
        !matcher("com.Foo*", "").matchesClass("com_Foo")
        matcher("com.Foo*", "").matchesClass("com.FooTest")

        !matcher("*Foo+Bar*", "").matchesClass("FooBar")
        matcher("*Foo+Bar*", "").matchesClass("xFoo+Barx")
    }

    def "supports wildcard"() {
        def m = matcher("*Foo*", "slow***Test")

        expect:
        m.matchesClass("com.Foo")
        m.matchesClass("FooTest")
        m.matchesClass("Foo")
        m.matchesClass(".Foo.")
        m.matchesClass("  Foo  ")

        m.matchesTest("Foo", "slowUiTest")
        m.matchesTest("Foo", "slowTest")
        m.matchesTest("Foo", "slow.Test")
        m.matchesTest("Foo", "slow***Test")
        m.matchesTest("Foo", "slow  Test")
    }

    def "knows if matches any method in class"() {
        expect:
        matcher("", "bar*").matchesAnyMethodIn(Bar)
        matcher("", "bar*").matchesAnyMethodIn(Foo)

        !matcher("", "*bar").matchesAnyMethodIn(Bar)
        !matcher("", "*bar").matchesAnyMethodIn(Foo)

        matcher("", "foo").matchesAnyMethodIn(Foo)
        !matcher("", "foo").matchesAnyMethodIn(Bar)
    }

    private class Bar {
        void barX() {}
    }

    private class Foo extends Bar {
        void foo() {}
    }
}
