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

import org.gradle.util.Matchers
import spock.lang.Specification

class DefaultTestSelectionSpecTest extends Specification {

    def "equals and hashcode"() {
        def spec = new DefaultTestSelectionSpec("foo", "bar")
        def differentClass = new DefaultTestSelectionSpec("xxx", "bar")
        def differentMethod = new DefaultTestSelectionSpec("foo", "ccc")
        def same = new DefaultTestSelectionSpec("foo", "bar")

        expect:
        Matchers.strictlyEquals(spec, same)
        spec != differentClass
        spec.hashCode() != differentClass.hashCode()
        spec != differentMethod
    }

    def "knows if test matches"() {
        def spec = new DefaultTestSelectionSpec("foo.*", ".*bar")

        expect:
        spec.matchesTest("fooxxx", "xxxbar")
        spec.matchesTest("foo", "bar")

        !spec.matchesTest("com.fooxxx", "xxxbar")
        !spec.matchesTest("fooxxx", "bar.foo")
    }

    def "knows if class matches"() {
        def spec = new DefaultTestSelectionSpec("foo.*", ".*bar")

        expect:
        spec.matchesClass("foo")
        spec.matchesClass("fooTest")

        !spec.matchesClass("com.foo")
    }

    def "knows if matches any method in class"() {
        expect:
        new DefaultTestSelectionSpec("", "bar.*").matchesAnyMethodIn(Bar)
        new DefaultTestSelectionSpec("", "bar.*").matchesAnyMethodIn(Foo)

        !new DefaultTestSelectionSpec("", ".*bar").matchesAnyMethodIn(Bar)
        !new DefaultTestSelectionSpec("", ".*bar").matchesAnyMethodIn(Foo)

        new DefaultTestSelectionSpec("", "foo").matchesAnyMethodIn(Foo)
        !new DefaultTestSelectionSpec("", "foo").matchesAnyMethodIn(Bar)
    }

    private class Bar {
        void barX() {}
    }

    private class Foo extends Bar {
        void foo() {}
    }
}
