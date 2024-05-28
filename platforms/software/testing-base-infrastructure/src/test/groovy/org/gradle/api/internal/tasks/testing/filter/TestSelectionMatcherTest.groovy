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
        expect:
        matcher(input, [], []).matchesTest(className, methodName) == match
        matcher([], [], input).matchesTest(className, methodName) == match

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

    def "knows if excluded test matches class"() {
        expect:
        matcher([], input, []).matchesTest(className, methodName) == match

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
        expect:
        matcher(input, [], []).matchesTest(className, methodName) == match
        matcher([], [], input).matchesTest(className, methodName) == match

        where:
        input                    | className                 | methodName            | match
        ["FooTest.test"]         | "FooTest"                 | "test"                | true
        ["FooTest.test"]         | "Footest"                 | "test"                | false
        ["FooTest.test"]         | "FooTest"                 | "TEST"                | false
        ["FooTest.test"]         | "com.foo.FooTest"         | "test"                | true
        ["FooTest.test"]         | "Foo.test"                | ""                    | false

        ["FooTest.*slow*"]       | "FooTest"                 | "slowUiTest"          | true
        ["FooTest.*slow*"]       | "FooTest"                 | "veryslowtest"        | true
        ["FooTest.*slow*"]       | "FooTest.SubTest"         | "slow"                | false
        ["FooTest.*slow*"]       | "FooTest"                 | "a slow test"         | true
        ["FooTest.*slow*"]       | "FooTest"                 | "aslow"               | true
        ["FooTest.*slow*"]       | "com.foo.FooTest"         | "slowUiTest"          | true
        ["FooTest.*slow*"]       | "FooTest"                 | "verySlowTest"        | false

        ["com.FooTest***slow*"]  | "com.FooTest"             | "slowMethod"          | true
        ["com.FooTest***slow*"]  | "com.FooTest2"            | "aslow"               | true
        ["com.FooTest***slow*"]  | "com.FooTest.OtherTest"   | "slow"                | true
        ["com.FooTest***slow*"]  | "FooTest"                 | "slowMethod"          | false
    }

    def "matches any of input"() {
        expect:
        matcher(input, [], []).matchesTest(className, methodName) == match
        matcher([], [], input).matchesTest(className, methodName) == match

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
        ["FooTest", "*BarTest"]             | "com.foo.FooTest"         | "xxxx"                | true
    }

    def "regexp chars are handled"() {
        expect:
        matcher(input, [], []).matchesTest(className, methodName) == match
        matcher([], [], input).matchesTest(className, methodName) == match

        where:
        input                               | className                 | methodName            | match
        ["*Foo+Bar*"]                       | "Foo+Bar"                 | "test"                | true
        ["*Foo+Bar*"]                       | "Foo+Bar"                 | "xxxx"                | true
        ["*Foo+Bar*"]                       | "com.Foo+Bar"             | "xxxx"                | true
        ["*Foo+Bar*"]                       | "FooBar"                  | "xxxx"                | false
    }

    def "handles null test method"() {
        expect:
        matcher(input, [], []).matchesTest(className, methodName) == match
        matcher([], [], input).matchesTest(className, methodName) == match

        where:
        input                               | className                 | methodName            | match
        ["FooTest"]                         | "FooTest"                 | null                  | true
        ["FooTest*"]                        | "FooTest"                 | null                  | true

        ["FooTest.*"]                       | "FooTest"                 | null                  | false
        ["FooTest"]                         | "OtherTest"               | null                  | false
        ["FooTest.test"]                    | "FooTest"                 | null                  | false
        ["FooTest.null"]                    | "FooTest"                 | null                  | false
    }

    def "script includes and command line includes both have to match"() {
        expect:
        matcher(input, [], inputCommandLine).matchesTest(className, methodName) == match

        where:
        input               | inputCommandLine | className  | methodName | match
        ["FooTest", "Bar" ] | []               | "FooTest"  | "whatever" | true
        ["FooTest"]         | ["Bar"]          | "FooTest"  | "whatever" | false
    }

    def 'can exclude as many classes as possible'() {
        expect:
        matcher(input, [], []).mayIncludeClass(fullQualifiedName) == maybeMatch
        matcher([], [], input).mayIncludeClass(fullQualifiedName) == maybeMatch

        where:
        input                             | fullQualifiedName    | maybeMatch
        ['.']                             | 'FooTest'            | false
        ['.FooTest.']                     | 'FooTest'            | false
        ['FooTest']                       | 'FooTest'            | true
        ['FooTest']                       | 'org.gradle.FooTest' | true
        ['FooTest']                       | 'org.foo.FooTest'    | true
        ['FooTest']                       | 'BarTest'            | false
        ['FooTest']                       | 'org.gradle.BarTest' | false
        ['FooTest.testMethod']            | 'FooTest'            | true
        ['FooTest.testMethod']            | 'BarTest'            | false
        ['FooTest.testMethod']            | 'org.gradle.FooTest' | true
        ['FooTest.testMethod']            | 'org.gradle.BarTest' | false
        ['org.gradle.FooTest.testMethod'] | 'FooTest'            | false
        ['org.gradle.FooTest.testMethod'] | 'org.gradle.FooTest' | true
        ['org.gradle.FooTest.testMethod'] | 'org.gradle.BarTest' | false
        ['org.foo.FooTest.testMethod']    | 'org.gradle.FooTest' | false
        ['org.foo.FooTest']               | 'org.gradle.FooTest' | false

        ['*FooTest*']                     | 'org.gradle.FooTest' | true
        ['*FooTest*']                     | 'aaa'                | true
        ['*FooTest']                      | 'org.gradle.FooTest' | true
        ['*FooTest']                      | 'FooTest'            | true
        ['*FooTest']                      | 'org.gradle.BarTest' | true // org.gradle.BarTest.testFooTest

        ['or*']                           | 'org.gradle.FooTest' | true
        ['org*']                          | 'org.gradle.FooTest' | true
        ['org.*']                         | 'org.gradle.FooTest' | true
        ['org.g*']                        | 'org.gradle.FooTest' | true
        ['org*']                          | 'FooTest'            | false
        ['org.*']                         | 'com.gradle.FooTest' | false
        ['org*']                          | 'com.gradle.FooTest' | false
        ['org.*']                         | 'com.gradle.FooTest' | false
        ['org.g*']                        | 'com.gradle.FooTest' | false
        ['FooTest*']                      | 'FooTest'            | true
        ['FooTest*']                      | 'org.gradle.FooTest' | true
        ['FooTest*']                      | 'BarTest'            | false
        ['FooTest*']                      | 'org.gradle.BarTest' | false
        ['org.gradle.FooTest*']           | 'org.gradle.BarTest' | false
        ['FooTest.testMethod*']           | 'FooTest'            | true
        ['FooTest.testMethod*']           | 'org.gradle.FooTest' | true
        ['org.foo.FooTest*']              | 'FooTest'            | false
        ['org.foo.FooTest*']              | 'org.gradle.FooTest' | false
        ['org.foo.*FooTest*']             | 'org.gradle.FooTest' | false
        ['org.foo.*FooTest*']             | 'org.foo.BarTest'    | true // org.foo.BarTest.testFooTest

        ['Foo']                           | 'FooTest'            | false
        ['org.gradle.Foo']                | 'org.gradle.FooTest' | false
        ['org.gradle.Foo.*']              | 'org.gradle.FooTest' | false

        ['org.gradle.Foo$Bar.*test']      | 'Foo'                | false
        ['org.gradle.Foo$Bar.*test']      | 'org.Foo'            | false
        ['org.gradle.Foo$Bar.*test']      | 'org.gradle.Foo'     | true
        ['Enclosing$Nested.test']         | "Enclosing"          | true
        ['org.gradle.Foo$1$2.test']       | "org.gradle.Foo"     | true
    }

    def 'can use multiple patterns'() {
        expect:
        matcher(pattern1, [], pattern2).mayIncludeClass(fullQualifiedName) == maybeMatch

        where:
        pattern1                | pattern2                        | fullQualifiedName     | maybeMatch
        ['']                    | ['com.my.Test.test[first.com]'] | 'com.my.Test'         | true
        ['FooTest*']            | ['FooTest']                     | 'FooTest'             | true
        ['FooTest*']            | ['BarTest*']                    | 'FooTest'             | false
        ['FooTest*']            | ['BarTest*']                    | 'FooBarTest'          | false
        []                      | []                              | 'anything'            | true
        ['org.gradle.FooTest*'] | ['org.gradle.BarTest*']         | 'org.gradle.FooTest'  | false
        ['org.gradle.FooTest*'] | ['*org.gradle.BarTest*']        | 'org.gradle.FooTest'  | true
    }

    def matcher(Collection<String> includedTests, Collection<String> excludedTests, Collection<String> includedTestsCommandLine) {
        return new TestSelectionMatcher(new TestFilterSpec(includedTests as Set, excludedTests as Set, includedTestsCommandLine as Set))
    }
}
