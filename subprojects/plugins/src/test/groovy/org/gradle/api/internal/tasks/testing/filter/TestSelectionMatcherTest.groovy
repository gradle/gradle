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
import spock.lang.Unroll

class TestSelectionMatcherTest extends Specification {

    def "knows if test matches class"() {
        expect: new TestSelectionMatcher(input,[]).matchesTest(className, methodName) == match

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

    def "knows if test matches class with exclude filter"() {
        expect: new TestSelectionMatcher([],input).matchesTest(className, methodName) == match

        where:
        input                    | className                 | methodName            | match
        ["FooTest"]              | "FooTest"                 | "whatever"            | false
        ["FooTest"]              | "fooTest"                 | "whatever"            | true

        ["com.foo.FooTest"]      | "com.foo.FooTest"         | "x"                   | false
        ["com.foo.FooTest"]      | "FooTest"                 | "x"                   | true
        ["com.foo.FooTest"]      | "com_foo_FooTest"         | "x"                   | true

        ["com.foo.FooTest.*"]    | "com.foo.FooTest"         | "aaa"                 | false
        ["com.foo.FooTest.*"]    | "com.foo.FooTest"         | "bbb"                 | false
        ["com.foo.FooTest.*"]    | "com.foo.FooTestx"        | "bbb"                 | true

        ["*.FooTest.*"]          | "com.foo.FooTest"         | "aaa"                 | false
        ["*.FooTest.*"]          | "com.bar.FooTest"         | "aaa"                 | false
        ["*.FooTest.*"]          | "FooTest"                 | "aaa"                 | true

        ["com*FooTest"]          | "com.foo.FooTest"         | "aaa"                 | false
        ["com*FooTest"]          | "com.FooTest"             | "bbb"                 | false
        ["com*FooTest"]          | "FooTest"                 | "bbb"                 | true

        ["*.foo.*"]              | "com.foo.FooTest"         | "aaaa"                | false
        ["*.foo.*"]              | "com.foo.bar.BarTest"     | "aaaa"                | false
        ["*.foo.*"]              | "foo.Test"                | "aaaa"                | true
        ["*.foo.*"]              | "fooTest"                 | "aaaa"                | true
        ["*.foo.*"]              | "foo"                     | "aaaa"                | true
    }

    def "knows if test matches"() {
        expect: new TestSelectionMatcher(input,[]).matchesTest(className, methodName) == match

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

    def "knows if test matches using exclude filter"() {
        expect: new TestSelectionMatcher([],input).matchesTest(className, methodName) == match

        where:
        input                    | className                 | methodName            | match
        ["FooTest.test"]         | "FooTest"                 | "test"                | false
        ["FooTest.test"]         | "Footest"                 | "test"                | true
        ["FooTest.test"]         | "FooTest"                 | "TEST"                | true
        ["FooTest.test"]         | "com.foo.FooTest"         | "test"                | true
        ["FooTest.test"]         | "Foo.test"                | ""                    | true

        ["FooTest.*slow*"]       | "FooTest"                 | "slowUiTest"          | false
        ["FooTest.*slow*"]       | "FooTest"                 | "veryslowtest"        | false
        ["FooTest.*slow*"]       | "FooTest.SubTest"         | "slow"                | false
        ["FooTest.*slow*"]       | "FooTest"                 | "a slow test"         | false
        ["FooTest.*slow*"]       | "FooTest"                 | "aslow"               | false
        ["FooTest.*slow*"]       | "com.foo.FooTest"         | "slowUiTest"          | true
        ["FooTest.*slow*"]       | "FooTest"                 | "verySlowTest"        | true

        ["com.FooTest***slow*"]  | "com.FooTest"             | "slowMethod"          | false
        ["com.FooTest***slow*"]  | "com.FooTest2"            | "aslow"               | false
        ["com.FooTest***slow*"]  | "com.FooTest.OtherTest"   | "slow"                | false
        ["com.FooTest***slow*"]  | "FooTest"                 | "slowMethod"          | true
    }

    def "matches any of input"() {
        expect: new TestSelectionMatcher(input,[]).matchesTest(className, methodName) == match

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

    def "matches any of input with exclude filter"() {
        expect: new TestSelectionMatcher([],input).matchesTest(className, methodName) == match

        where:
        input                               | className                 | methodName            | match
        ["FooTest.test", "FooTest.bar"]     | "FooTest"                 | "test"                | false
        ["FooTest.test", "FooTest.bar"]     | "FooTest"                 | "bar"                 | false
        ["FooTest.test", "FooTest.bar"]     | "FooTest"                 | "baz"                 | true
        ["FooTest.test", "FooTest.bar"]     | "Footest"                 | "test"                | true

        ["FooTest.test", "BarTest.*"]       | "FooTest"                 | "test"                | false
        ["FooTest.test", "BarTest.*"]       | "BarTest"                 | "xxxx"                | false
        ["FooTest.test", "BarTest.*"]       | "FooTest"                 | "xxxx"                | true

        ["FooTest.test", "FooTest.*fast*"]  | "FooTest"                 | "test"                | false
        ["FooTest.test", "FooTest.*fast*"]  | "FooTest"                 | "fast"                | false
        ["FooTest.test", "FooTest.*fast*"]  | "FooTest"                 | "a fast test"         | false
        ["FooTest.test", "FooTest.*fast*"]  | "FooTest"                 | "xxxx"                | true

        ["FooTest", "*BarTest"]             | "FooTest"                 | "test"                | false
        ["FooTest", "*BarTest"]             | "FooTest"                 | "xxxx"                | false
        ["FooTest", "*BarTest"]             | "BarTest"                 | "xxxx"                | false
        ["FooTest", "*BarTest"]             | "com.foo.BarTest"         | "xxxx"                | false
        ["FooTest", "*BarTest"]             | "com.foo.FooTest"         | "xxxx"                | true
    }

    def "regexp chars are handled"() {
        expect: new TestSelectionMatcher(input,[]).matchesTest(className, methodName) == match

        where:
        input                               | className                 | methodName            | match
        ["*Foo+Bar*"]                       | "Foo+Bar"                 | "test"                | true
        ["*Foo+Bar*"]                       | "Foo+Bar"                 | "xxxx"                | true
        ["*Foo+Bar*"]                       | "com.Foo+Bar"             | "xxxx"                | true
        ["*Foo+Bar*"]                       | "FooBar"                  | "xxxx"                | false
    }

    def "regexp chars are handled in excludes"() {
        expect: new TestSelectionMatcher([],input).matchesTest(className, methodName) == match

        where:
        input                               | className                 | methodName            | match
        ["*Foo+Bar*"]                       | "Foo+Bar"                 | "test"                | false
        ["*Foo+Bar*"]                       | "Foo+Bar"                 | "xxxx"                | false
        ["*Foo+Bar*"]                       | "com.Foo+Bar"             | "xxxx"                | false
        ["*Foo+Bar*"]                       | "FooBar"                  | "xxxx"                | true
    }

    def "handles null test method"() {
        expect: new TestSelectionMatcher(input,[]).matchesTest(className, methodName) == match

        where:
        input                               | className                 | methodName            | match
        ["FooTest"]                         | "FooTest"                 | null                  | true
        ["FooTest*"]                        | "FooTest"                 | null                  | true

        ["FooTest.*"]                       | "FooTest"                 | null                  | false
        ["FooTest"]                         | "OtherTest"               | null                  | false
        ["FooTest.test"]                    | "FooTest"                 | null                  | false
        ["FooTest.null"]                    | "FooTest"                 | null                  | false
    }

    def "handles null test method in excludes"() {
        expect: new TestSelectionMatcher([],input).matchesTest(className, methodName) == match

        where:
        input                               | className                 | methodName            | match
        ["FooTest"]                         | "FooTest"                 | null                  | false
        ["FooTest*"]                        | "FooTest"                 | null                  | false

        ["FooTest.*"]                       | "FooTest"                 | null                  | true
        ["FooTest"]                         | "OtherTest"               | null                  | true
        ["FooTest.test"]                    | "FooTest"                 | null                  | true
        ["FooTest.null"]                    | "FooTest"                 | null                  | true
    }

    @Unroll("For includes #includes and excludes #excludes, match for #className##methodName should be #match")
    def "can combine an include filter with an exclude filter"() {
        expect:
        new TestSelectionMatcher(includes, excludes).matchesTest(className, methodName) == match

        where:
        includes         | excludes         | className   | methodName | match
        []               | []               | "FooTest"   | 'test'     | true
        []               | ['FooTest.test'] | "FooTest"   | 'test'     | false
        []               | ['*']            | "FooTest"   | 'test'     | false

        ["*"]            | []               | "FooTest"   | 'test'     | true
        ["*"]            | ['FooTest.test'] | "FooTest"   | 'test'     | false
        ["*"]            | ['*']            | "FooTest"   | 'test'     | false

        ["FooTest"]      | []               | "FooTest"   | 'foo'      | true
        ["FooTest*"]     | ['FooTest.*']    | "FooTest"   | 'foo'      | false

        ["FooTest.*"]    | []               | "FooTest"   | 'foo'      | true
        ["FooTest.*"]    | ['FooTest.foo']  | "FooTest"   | 'foo'      | false

        ["FooTest"]      | []               | "OtherTest" | 'foo'      | false
        ["FooTest"]      | ['FooTest.foo']  | "OtherTest" | 'foo'      | false

        ["FooTest.test"] | []               | "FooTest"   | 'test'     | true
        ["FooTest.*"]    | ['FooTest.test'] | "FooTest"   | 'foo'      | true

        ["FooTest.test"] | []               | "FooTest"   | 'test'     | true
        ["FooTest.test"] | ['FooTest.test'] | "FooTest"   | 'test'     | false

    }
}
