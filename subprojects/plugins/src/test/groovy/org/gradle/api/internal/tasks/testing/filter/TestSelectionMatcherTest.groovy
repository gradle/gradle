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

package org.gradle.api.internal.tasks.testing.filter

import spock.lang.Specification

class TestSelectionMatcherTest extends Specification {

    def "knows if test matches class"() {
        expect: new TestSelectionMatcher(input).matchesTest(className, methodName) == match

        where:
        input                    | className                 | methodName            | match
        ["FooTest"]              | "FooTest"                 | "whatever"            | true
        ["FooTest"]              | "fooTest"                 | "whatever"            | false

        ["com.foo.FooTest"]      | "com.foo.FooTest"         | "x"                   | true
        ["com.foo.FooTest"]      | "FooTest"                 | "x"                   | false
        ["com.foo.FooTest"]      | "com_foo_FooTest"         | "x"                   | false

        ["com.foo.FooTest.*"]    | "com.foo.FooTest"         | "aaa"                 | true
        ["com.foo.FooTest.*"]    | "com.foo.FooTest"         | "bbb"                 | true
        ["com.foo.FooTest.*"]    | "com.foo.FooTestx"        | "bbb"                 | false

        ["*.FooTest.*"]          | "com.foo.FooTest"         | "aaa"                 | true
        ["*.FooTest.*"]          | "com.bar.FooTest"         | "aaa"                 | true
        ["*.FooTest.*"]          | "FooTest"                 | "aaa"                 | false

        ["com*FooTest"]          | "com.foo.FooTest"         | "aaa"                 | true
        ["com*FooTest"]          | "com.FooTest"             | "bbb"                 | true
        ["com*FooTest"]          | "FooTest"                 | "bbb"                 | false

        ["*.foo.*"]              | "com.foo.FooTest"         | "aaaa"                | true
        ["*.foo.*"]              | "com.foo.bar.BarTest"     | "aaaa"                | true
        ["*.foo.*"]              | "foo.Test"                | "aaaa"                | false
        ["*.foo.*"]              | "fooTest"                 | "aaaa"                | false
        ["*.foo.*"]              | "foo"                     | "aaaa"                | false
    }

    def "knows if test matches"() {
        expect: new TestSelectionMatcher(input).matchesTest(className, methodName) == match

        where:
        input                    | className                 | methodName            | match
        ["FooTest.test"]         | "FooTest"                 | "test"                | true
        ["FooTest.test"]         | "Footest"                 | "test"                | false
        ["FooTest.test"]         | "FooTest"                 | "TEST"                | false
        ["FooTest.test"]         | "com.foo.FooTest"         | "test"                | false
        ["FooTest.test"]         | "Foo.test"                | ""                    | false

        ["FooTest.*slow*"]       | "FooTest"                 | "slowUiTest"          | true
        ["FooTest.*slow*"]       | "FooTest"                 | "veryslowtest"        | true
        ["FooTest.*slow*"]       | "FooTest.SubTest"         | "slow"                | true
        ["FooTest.*slow*"]       | "FooTest"                 | "a slow test"         | true
        ["FooTest.*slow*"]       | "FooTest"                 | "aslow"               | true
        ["FooTest.*slow*"]       | "com.foo.FooTest"         | "slowUiTest"          | false
        ["FooTest.*slow*"]       | "FooTest"                 | "verySlowTest"        | false

        ["com.FooTest***slow*"]  | "com.FooTest"             | "slowMethod"          | true
        ["com.FooTest***slow*"]  | "com.FooTest2"            | "aslow"               | true
        ["com.FooTest***slow*"]  | "com.FooTest.OtherTest"   | "slow"                | true
        ["com.FooTest***slow*"]  | "FooTest"                 | "slowMethod"          | false
    }

    def "matches any of input"() {
        expect: new TestSelectionMatcher(input).matchesTest(className, methodName) == match

        where:
        input                               | className                 | methodName            | match
        ["FooTest.test", "FooTest.bar"]     | "FooTest"                 | "test"                | true
        ["FooTest.test", "FooTest.bar"]     | "FooTest"                 | "bar"                 | true
        ["FooTest.test", "FooTest.bar"]     | "FooTest"                 | "baz"                 | false
        ["FooTest.test", "FooTest.bar"]     | "Footest"                 | "test"                | false

        ["FooTest.test", "BarTest.*"]       | "FooTest"                 | "test"                | true
        ["FooTest.test", "BarTest.*"]       | "BarTest"                 | "xxxx"                | true
        ["FooTest.test", "BarTest.*"]       | "FooTest"                 | "xxxx"                | false

        ["FooTest.test", "FooTest.*fast*"]  | "FooTest"                 | "test"                | true
        ["FooTest.test", "FooTest.*fast*"]  | "FooTest"                 | "fast"                | true
        ["FooTest.test", "FooTest.*fast*"]  | "FooTest"                 | "a fast test"         | true
        ["FooTest.test", "FooTest.*fast*"]  | "FooTest"                 | "xxxx"                | false

        ["FooTest", "*BarTest"]             | "FooTest"                 | "test"                | true
        ["FooTest", "*BarTest"]             | "FooTest"                 | "xxxx"                | true
        ["FooTest", "*BarTest"]             | "BarTest"                 | "xxxx"                | true
        ["FooTest", "*BarTest"]             | "com.foo.BarTest"         | "xxxx"                | true
        ["FooTest", "*BarTest"]             | "com.foo.FooTest"         | "xxxx"                | false
    }

    def "regexp chars are handled"() {
        expect: new TestSelectionMatcher(input).matchesTest(className, methodName) == match

        where:
        input                               | className                 | methodName            | match
        ["*Foo+Bar*"]                       | "Foo+Bar"                 | "test"                | true
        ["*Foo+Bar*"]                       | "Foo+Bar"                 | "xxxx"                | true
        ["*Foo+Bar*"]                       | "com.Foo+Bar"             | "xxxx"                | true
        ["*Foo+Bar*"]                       | "FooBar"                  | "xxxx"                | false
    }

    def "handles null test method"() {
        expect: new TestSelectionMatcher(input).matchesTest(className, methodName) == match

        where:
        input                               | className                 | methodName            | match
        ["FooTest"]                         | "FooTest"                 | null                  | true
        ["FooTest*"]                        | "FooTest"                 | null                  | true

        ["FooTest.*"]                       | "FooTest"                 | null                  | false
        ["FooTest"]                         | "OtherTest"               | null                  | false
        ["FooTest.test"]                    | "FooTest"                 | null                  | false
        ["FooTest.null"]                    | "FooTest"                 | null                  | false
    }
}
