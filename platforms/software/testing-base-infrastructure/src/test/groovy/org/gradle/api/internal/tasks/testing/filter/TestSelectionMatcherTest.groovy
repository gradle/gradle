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


import spock.lang.Issue
import spock.lang.Specification

class TestSelectionMatcherTest extends Specification {

    def "knows if test matches class"() {
        expect:
        assertMatchesTest(pattern, patternMatches, patternDoesNotMatch)

        where:
        // patternMatches and patternDoesNotMatch entries are [className, methodName] tuples
        pattern               | patternMatches                                                 | patternDoesNotMatch
        ["FooTest"]           | [["FooTest", "whatever"]]                                      | [["fooTest", "whatever"]]
        ["com.foo.FooTest"]   | [["com.foo.FooTest", "x"]]                                     | [["FooTest", "x"], ["com_foo_FooTest", "x"]]
        ["com.foo.FooTest.*"] | [["com.foo.FooTest", "aaa"], ["com.foo.FooTest", "bbb"]]       | [["com.foo.FooTestx", "bbb"]]
        ["*.FooTest.*"]       | [["com.foo.FooTest", "aaa"], ["com.bar.FooTest", "aaa"]]       | [["FooTest", "aaa"]]
        ["com*FooTest"]       | [["com.foo.FooTest", "aaa"], ["com.FooTest", "bbb"]]           | [["FooTest", "bbb"]]
        ["*.foo.*"]           | [["com.foo.FooTest", "aaaa"], ["com.foo.bar.BarTest", "aaaa"]] | [["foo.Test", "aaaa"], ["fooTest", "aaaa"], ["foo", "aaaa"]]
    }

    def "knows if excluded test matches class"() {
        expect:
        for (pair in patternMatches) {
            def (className, methodName) = pair
            assert !matcher([], pattern, []).matchesTest(className, methodName) : "excludedTests=$pattern expected to match [$className, $methodName]"
        }
        for (pair in patternDoesNotMatch) {
            def (className, methodName) = pair
            assert matcher([], pattern, []).matchesTest(className, methodName) : "excludedTests=$pattern expected NOT to match [$className, $methodName]"
        }

        where:
        // patternMatches and patternDoesNotMatch entries are [className, methodName] tuples
        pattern               | patternMatches                                                 | patternDoesNotMatch
        ["FooTest"]           | [["FooTest", "whatever"]]                                      | [["fooTest", "whatever"]]
        ["com.foo.FooTest"]   | [["com.foo.FooTest", "x"]]                                     | [["FooTest", "x"], ["com_foo_FooTest", "x"]]
        ["com.foo.FooTest.*"] | [["com.foo.FooTest", "aaa"], ["com.foo.FooTest", "bbb"]]       | [["com.foo.FooTestx", "bbb"]]
        ["*.FooTest.*"]       | [["com.foo.FooTest", "aaa"], ["com.bar.FooTest", "aaa"]]       | [["FooTest", "aaa"]]
        ["com*FooTest"]       | [["com.foo.FooTest", "aaa"], ["com.FooTest", "bbb"]]           | [["FooTest", "bbb"]]
        ["*.foo.*"]           | [["com.foo.FooTest", "aaaa"], ["com.foo.bar.BarTest", "aaaa"]] | [["foo.Test", "aaaa"], ["fooTest", "aaaa"], ["foo", "aaaa"]]
    }

    def "knows if test matches"() {
        expect:
        assertMatchesTest(pattern, patternMatches, patternDoesNotMatch)

        where:
        // patternMatches and patternDoesNotMatch entries are [className, methodName] tuples
        pattern                 | patternMatches                                                                                                                                                              | patternDoesNotMatch
        ["FooTest.test"]        | [["FooTest", "test"], ["com.foo.FooTest", "test"]]                                                                                                                          | [["Footest", "test"], ["FooTest", "TEST"], ["Foo.test", ""]]
        ["FooTest.*slow*"]      | [["FooTest", "slowUiTest"], ["FooTest", "veryslowtest"], ["FooTest", "a slow test"], ["FooTest", "aslow"], ["com.foo.FooTest", "slowUiTest"]]                               | [["FooTest.SubTest", "slow"], ["FooTest", "verySlowTest"]]
        ["com.FooTest***slow*"] | [["com.FooTest", "slowMethod"], ["com.FooTest2", "aslow"], ["com.FooTest.OtherTest", "slow"]]                                                                               | [["FooTest", "slowMethod"]]
    }

    def "matches any of input"() {
        expect:
        assertMatchesTest(pattern, patternMatches, patternDoesNotMatch)

        where:
        // patternMatches and patternDoesNotMatch entries are [className, methodName] tuples
        pattern                            | patternMatches                                                                                                                    | patternDoesNotMatch
        ["FooTest.test", "FooTest.bar"]    | [["FooTest", "test"], ["FooTest", "bar"]]                                                                                         | [["FooTest", "baz"], ["Footest", "test"]]
        ["FooTest.test", "BarTest.*"]      | [["FooTest", "test"], ["BarTest", "xxxx"]]                                                                                        | [["FooTest", "xxxx"]]
        ["FooTest.test", "FooTest.*fast*"] | [["FooTest", "test"], ["FooTest", "fast"], ["FooTest", "a fast test"]]                                                            | [["FooTest", "xxxx"]]
        ["FooTest", "*BarTest"]            | [["FooTest", "test"], ["FooTest", "xxxx"], ["BarTest", "xxxx"], ["com.foo.BarTest", "xxxx"], ["com.foo.FooTest", "xxxx"]]         | [["BazTest", "xxxx"]]
    }

    def "globbing is handled"() {
        expect:
        assertMatchesTest(pattern, patternMatches, patternDoesNotMatch)

        where:
        // patternMatches and patternDoesNotMatch entries are [className, methodName] tuples
        pattern        | patternMatches                                                                                    | patternDoesNotMatch
        ["*Foo+Bar*"]  | [["Foo+Bar", "test"], ["Foo+Bar", "xxxx"], ["com.Foo+Bar", "xxxx"], ["com.Foo+BarBaz", "xxxx"]]   | [["FooBar", "xxxx"], ["Foo+bar", "xxxx"]]
        ["*Foo*Bar"]   | [["FooSomethingBar", "test"], ["Foo---Bar", "test"], ["com.Foo-Bar", "xxxx"], ["FooBar", "xxxx"]] | [["Foobar", "xxxx"], ["FooBaar", "xxxx"]]
    }

    def "handles null test method"() {
        expect:
        assertMatchesTest(pattern, patternMatches, patternDoesNotMatch)

        where:
        // patternMatches and patternDoesNotMatch entries are [className, methodName] tuples
        pattern           | patternMatches          | patternDoesNotMatch
        ["FooTest"]       | [["FooTest", null]]     | [["OtherTest", null]]
        ["FooTest*"]      | [["FooTest", null]]     | [["OtherTest", null]]
        ["FooTest.*"]     | [["FooTest", "test"]]   | [["FooTest", null]]
        ["FooTest.test"]  | [["FooTest", "test"]]   | [["FooTest", null]]
        ["FooTest.null"]  | []                      | [["FooTest", null]]
    }

    def "script includes and command line includes both have to match"() {
        expect:
        for (pair in matches) {
            def (className, methodName) = pair
            assert matcher(buildScript, [], inputCommandLine).matchesTest(className, methodName) : "input=$buildScript, inputCommandLine=$inputCommandLine expected to match [$className, $methodName]"
        }
        for (pair in doesNotMatch) {
            def (className, methodName) = pair
            assert !matcher(buildScript, [], inputCommandLine).matchesTest(className, methodName) : "input=$buildScript, inputCommandLine=$inputCommandLine expected NOT to match [$className, $methodName]"
        }

        where:
        // matches and doesNotMatch entries are [className, methodName] tuples
        buildScript         | inputCommandLine | matches                                        | doesNotMatch
        ["FooTest", "Bar"]  | []               | [["FooTest", "whatever"], ["Bar", "whatever"]] | [["Baz", "whatever"]]
        ["FooTest"]         | ["Bar"]          | []                                             | [["FooTest", "whatever"], ["Bar", "whatever"]]
        ["FooTest"]         | ["FooTest"]      | [["FooTest", "whatever"]]                      | [["Bar", "whatever"]]
    }

    def 'can exclude as many classes as possible with mayIncludeClass'() {
        expect:
        for (fqn in patternMatches) {
            assert matcher(pattern, [], []).mayIncludeClass(fqn) : "includedTests=$pattern expected to possibly include $fqn"
            assert matcher([], [], pattern).mayIncludeClass(fqn) : "includedTestsCommandLine=$pattern expected to possibly include $fqn"
        }
        for (fqn in patternDoesNotMatch) {
            assert !matcher(pattern, [], []).mayIncludeClass(fqn) : "includedTests=$pattern expected NOT to possibly include $fqn"
            assert !matcher([], [], pattern).mayIncludeClass(fqn) : "includedTestsCommandLine=$pattern expected NOT to possibly include $fqn"
        }

        where:
        pattern                           | patternMatches                                                             | patternDoesNotMatch
        ['.']                             | []                                                                         | ['FooTest']
        ['.FooTest.']                     | []                                                                         | ['FooTest']
        ['FooTest']                       | ['FooTest', 'org.gradle.FooTest', 'org.foo.FooTest']                       | ['BarTest', 'org.gradle.BarTest']
        ['FooTest.testMethod']            | ['FooTest', 'org.gradle.FooTest']                                          | ['BarTest', 'org.gradle.BarTest']
        ['org.gradle.FooTest.testMethod'] | ['org.gradle.FooTest']                                                     | ['FooTest', 'org.gradle.BarTest']
        ['org.foo.FooTest.testMethod']    | []                                                                         | ['org.gradle.FooTest']
        ['org.foo.FooTest']               | []                                                                         | ['org.gradle.FooTest']

        ['*FooTest*']                     | ['org.gradle.FooTest', 'aaa']                                              | []
        ['*FooTest']                      | ['org.gradle.FooTest', 'FooTest', 'org.gradle.BarTest' /* .testFooTest */] | []

        ['or*']                           | ['org.gradle.FooTest']                                                     | []
        ['org*']                          | ['org.gradle.FooTest']                                                     | ['FooTest', 'com.gradle.FooTest']
        ['org.*']                         | ['org.gradle.FooTest']                                                     | ['com.gradle.FooTest']
        ['org.g*']                        | ['org.gradle.FooTest']                                                     | ['com.gradle.FooTest']
        ['FooTest*']                      | ['FooTest', 'org.gradle.FooTest']                                          | ['BarTest', 'org.gradle.BarTest']
        ['org.gradle.FooTest*']           | []                                                                         | ['org.gradle.BarTest']
        ['FooTest.testMethod*']           | ['FooTest', 'org.gradle.FooTest']                                          | []
        ['org.foo.FooTest*']              | []                                                                         | ['FooTest', 'org.gradle.FooTest']
        ['org.foo.*FooTest*']             | ['org.foo.BarTest' /* .testFooTest */]                                     | ['org.gradle.FooTest']

        ['Foo']                           | []                                                                         | ['FooTest']
        ['org.gradle.Foo']                | []                                                                         | ['org.gradle.FooTest']
        ['org.gradle.Foo.*']              | []                                                                         | ['org.gradle.FooTest']

        ['org.gradle.Foo$Bar.*test']      | ['org.gradle.Foo']                                                         | ['Foo', 'org.Foo']
        ['Enclosing$Nested.test']         | ['Enclosing']                                                              | []
        ['org.gradle.Foo$1$2.test']       | ['org.gradle.Foo']                                                         | []
    }

    def 'can use multiple patterns with mayIncludeClass'() {
        expect:
        for (fqn in matches) {
            assert matcher(pattern1, [], pattern2).mayIncludeClass(fqn) : "pattern1=$pattern1, pattern2=$pattern2 expected to possibly include $fqn"
        }
        for (fqn in doesNotMatch) {
            assert !matcher(pattern1, [], pattern2).mayIncludeClass(fqn) : "pattern1=$pattern1, pattern2=$pattern2 expected NOT to possibly include $fqn"
        }

        where:
        pattern1                | pattern2                        | matches                | doesNotMatch
        []                      | []                              | ['anything']           | []
        ['']                    | ['com.my.Test.test[first.com]'] | ['com.my.Test']        | []
        ['FooTest*']            | ['FooTest']                     | ['FooTest']            | []
        ['FooTest*']            | ['BarTest*']                    | []                     | ['FooTest', 'FooBarTest']
        ['org.gradle.FooTest*'] | ['org.gradle.BarTest*']         | []                     | ['org.gradle.FooTest']
        ['org.gradle.FooTest*'] | ['*org.gradle.BarTest*']        | ['org.gradle.FooTest'] | []
    }

    def "matchesIncludeTest ignores exclude patterns"() {
        expect:
        for (pair in matches) {
            def (className, methodName) = pair
            assert matcher(includes, excludes, []).matchesIncludeTest(className, methodName) : "includes=$includes, excludes=$excludes expected to match [$className, $methodName]"
        }
        for (pair in doesNotMatch) {
            def (className, methodName) = pair
            assert !matcher(includes, excludes, []).matchesIncludeTest(className, methodName) : "includes=$includes, excludes=$excludes expected NOT to match [$className, $methodName]"
        }

        where:
        // matches and doesNotMatch entries are [className, methodName] tuples
        includes         | excludes        | matches                                                     | doesNotMatch
        []               | ["FooTest"]     | [["FooTest", "aaa"]]                                        | []
        ["FooTest"]      | ["FooTest"]     | [["FooTest", null], ["FooTest", "aaa"], ["FooTest", "bbb"]] | [["BarTest", "aaa"]]
        ["FooTest.aaa"]  | ["FooTest"]     | [["FooTest", "aaa"]]                                        | [["FooTest", null], ["FooTest", "bbb"], ["BarTest", "aaa"]]
        ["FooTest"]      | ["FooTest.aaa"] | [["FooTest", null], ["FooTest", "aaa"], ["FooTest", "bbb"]] | [["BarTest", "aaa"]]
    }

    def "matchesIncludeTest returns the AND of build-script and command-line includes"() {
        expect:
        for (className in matches) {
            assert matcher(buildScript, [], commandLine).matchesIncludeTest(className, null) : "buildScript=$buildScript, commandLine=$commandLine expected to match $className"
        }
        for (className in doesNotMatch) {
            assert !matcher(buildScript, [], commandLine).matchesIncludeTest(className, null) : "buildScript=$buildScript, commandLine=$commandLine expected NOT to match $className"
        }

        where:
        buildScript  | commandLine  | matches      | doesNotMatch
        []           | []           | ["FooTest"]  | []
        ["FooTest"]  | []           | ["FooTest"]  | ["BarTest"]
        []           | ["FooTest"]  | ["FooTest"]  | ["BarTest"]
        ["FooTest"]  | ["FooTest"]  | ["FooTest"]  | ["BarTest"]
        ["FooTest"]  | ["BarTest"]  | []           | ["FooTest", "BarTest"]
    }

    def "matchesExcludeTest correctly matches on exclude patterns"() {
        expect:
        for (pair in patternMatches) {
            def (className, methodName) = pair
            assert matcher([], pattern, []).matchesExcludeTest(className, methodName) : "excludedTests=$pattern expected to match [$className, $methodName]"
        }
        for (pair in patternDoesNotMatch) {
            def (className, methodName) = pair
            assert !matcher([], pattern, []).matchesExcludeTest(className, methodName) : "excludedTests=$pattern expected NOT to match [$className, $methodName]"
        }

        where:
        // patternMatches and patternDoesNotMatch entries are [className, methodName] tuples
        pattern             | patternMatches                              | patternDoesNotMatch
        []                  | []                                          | [["FooTest", null], ["FooTest", "doThing"], ["BarTest", null], ["BarTest", "doThing"]]
        ["FooTest"]         | [["FooTest", null], ["FooTest", "doThing"]] | [["BarTest", null], ["BarTest", "doThing"]]
        // Note that ["FooTest", null] matches because of multiple ways to match on method patterns - see ClassTestSelectionMatcher.matchesExcludeTest()
        ["FooTest.doThing"] | [["FooTest", null], ["FooTest", "doThing"]] | [["FooTest", "doOther"], ["BarTest", null], ["BarTest", "doThing"]]
    }

    def "matchesExcludeTest ignores include patterns"() {
        expect:
        for (pair in matches) {
            def (className, methodName) = pair
            assert matcher(includes, excludes, []).matchesExcludeTest(className, methodName) : "includes=$includes, excludes=$excludes expected to match [$className, $methodName]"
        }
        for (pair in doesNotMatch) {
            def (className, methodName) = pair
            assert !matcher(includes, excludes, []).matchesExcludeTest(className, methodName) : "includes=$includes, excludes=$excludes expected NOT to match [$className, $methodName]"
        }

        where:
        // matches and doesNotMatch entries are [className, methodName] tuples
        includes            | excludes            | matches                                     | doesNotMatch
        ["BarTest"]         | ["FooTest"]         | [["FooTest", null], ["FooTest", "doThing"]] | [["BarTest", null], ["BarTest", "doThing"]]
        ["FooTest"]         | ["FooTest"]         | [["FooTest", null], ["FooTest", "doThing"]] | [["BarTest", null], ["BarTest", "doThing"]]
        // Note that ["FooTest", null] matches because of multiple ways to match on method patterns - see ClassTestSelectionMatcher.matchesExcludeTest()
        ["BarTest"]         | ["FooTest.doThing"] | [["FooTest", null], ["FooTest", "doThing"]] | [["FooTest", "doOther"], ["BarTest", null], ["BarTest", "doThing"]]
        ["FooTest"]         | ["FooTest.doThing"] | [["FooTest", null], ["FooTest", "doThing"]] | [["FooTest", "doOther"], ["BarTest", null], ["BarTest", "doThing"]]
        ["FooTest.doThing"] | ["FooTest.doThing"] | [["FooTest", null], ["FooTest", "doThing"]] | [["FooTest", "doOther"], ["BarTest", null], ["BarTest", "doThing"]]
    }

    @Issue("https://github.com/gradle/gradle/issues/37539")
    def "matchesExcludeClass is exact and does not treat parent pattern as matching nested class"() {
        expect:
        for (className in patternMatches) {
            assert matcher([], [pattern], []).matchesExcludeClassExactly(className) : "excludedTests=[$pattern] expected to match exactly $className"
        }
        for (className in patternDoesNotMatch) {
            assert !matcher([], [pattern], []).matchesExcludeClassExactly(className) : "excludedTests=[$pattern] expected NOT to match exactly $className"
        }

        where:
        pattern                                           | patternMatches                                                                                   | patternDoesNotMatch
        "SampleTest"                                      | ["SampleTest"]                                                                                   | ["SampleTest\$NestedTestClass", "SampleTest\$NestedTestClass\$SubNestedTestClass"]
        "SampleTest\$NestedTestClass"                     | ["SampleTest\$NestedTestClass"]                                                                  | ["SampleTest", "SampleTest\$NestedTestClass\$SubNestedTestClass"]
        "SampleTest\$NestedTestClass\$SubNestedTestClass" | ["SampleTest\$NestedTestClass\$SubNestedTestClass"]                                              | ["SampleTest", "SampleTest\$NestedTestClass"]
        "SampleTest*"                                     | ["SampleTest", "SampleTest\$NestedTestClass", "SampleTest\$NestedTestClass\$SubNestedTestClass"] | ["NotSampleTest", "NotSampleTest\$NestedTestClass"]
    }

    def matcher(Collection<String> includedTests, Collection<String> excludedTests, Collection<String> includedTestsCommandLine) {
        return new TestSelectionMatcher(new TestFilterSpec(includedTests as Set, excludedTests as Set, includedTestsCommandLine as Set))
    }

    private void assertMatchesTest(List<String> pattern,
                                   List<List<String>> patternMatches,
                                   List<List<String>> patternDoesNotMatch) {
        for (pair in patternMatches) {
            def (className, methodName) = pair
            assert matcher(pattern, [], []).matchesTest(className, methodName) : "includedTests=$pattern expected to match [$className, $methodName]"
            assert matcher([], [], pattern).matchesTest(className, methodName) : "includedTestsCommandLine=$pattern expected to match [$className, $methodName]"
        }
        for (pair in patternDoesNotMatch) {
            def (className, methodName) = pair
            assert !matcher(pattern, [], []).matchesTest(className, methodName) : "includedTests=$pattern expected NOT to match [$className, $methodName]"
            assert !matcher([], [], pattern).matchesTest(className, methodName) : "includedTestsCommandLine=$pattern expected NOT to match [$className, $methodName]"
        }
    }
}
