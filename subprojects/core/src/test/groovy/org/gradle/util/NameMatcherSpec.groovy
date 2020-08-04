/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.util

import org.gradle.internal.Cast
import spock.lang.Shared
import spock.lang.Specification

import static com.google.common.collect.Iterables.concat
import static com.google.common.collect.Lists.newArrayList
import static java.util.Collections.singletonMap
import static org.gradle.util.WrapUtil.toList

/*TODO rename to NameMatcherSpec and remove origin */
class NameMatcherSpec extends Specification {

    @Shared
    NameMatcher matcher

    def setup() {
        matcher = new NameMatcher()
    }

    def "selects exact match"() {
        expect:
        assertMatches("name", "name") /*TODO use single quotes where possible*/
        assertMatches("name", "name", "other")
    }

    /*TODO convert to parameterized test */
    def "selects item with matching prefix"() {
        expect:
        assertMatches("na", "name")
        assertMatches("na", "name", "other")
        // Mixed case
        assertMatches("na", "Name")
        assertMatches("NA", "name")
        assertMatches("somena", "someName")
        assertMatches("somena", "SomeName")
        assertMatches("somena", "SomeName")
        assertMatches("some na", "Some Name")
    }

    def "selects item with matching camel case prefix"() {
        expect:
        assertMatches("sN", "someName")
        assertMatches("soN", "someName")
        assertMatches("SN", "someName")
        assertMatches("SN", "SomeName")
        assertMatches("SN", "SomeNameWithExtraStuff")
        assertMatches("so_n", "some_name")
        assertMatches("so_n", "some_Name")
        assertMatches("so_n_wi_ext", "some_Name_with_EXTRA")
        assertMatches("so.n", "some.name")
        assertMatches("so n", "some name")
        assertMatches("ABC", "ABC")
        assertMatches("a9N", "a9Name")
        assertMatches("a9N", "abc9Name")
        assertMatches("a9n", "abc9Name")
    }

    def "prefers exact match over case insensitive match"() {
        expect:
        assertMatches("name", "name", "Name", "NAME")
        assertMatches("someName", "someName", "SomeName", "somename", "SOMENAME")
        assertMatches("some Name", "some Name", "Some Name", "some name", "SOME NAME")
    }

    def "prefers exact match over partial match"() {
        expect:
        assertMatches("name", "name", "nam", "n", "NAM")
    }

    def "prefers exact match over prefix match"() {
        expect:
        assertMatches("someName", "someName", "someNameWithExtra")
    }

    def "prefers exact match over camel case match"() {
        expect:
        assertMatches("sName", "sName", "someName", "sNames")
        assertMatches("so Name", "so Name", "some Name", "so name")
        assertMatches("ABC", "ABC", "AaBbCc")
    }

    def "prefers full camel case match over camel case prefix"() {
        expect:
        assertMatches("sN", "someName", "someNameWithExtra")
        assertMatches("name", "names", "nameWithExtra")
        assertMatches("s_n", "some_name", "some_name_with_extra")
    }

    def "prefers case sensitive camel case match over case insensitive camel case match"() {
        expect:
        assertMatches("soNa", "someName", "somename")
        assertMatches("SN", "SomeName", "someName")
        assertMatches("na1", "name1", "Name1", "NAME1")
    }

    def "prefers case insensitive match over camel case match"() {
        expect:
        assertMatches("somename", "someName", "someNameWithExtra")
        assertMatches("soNa", "sona", "someName")
    }

    def "does not select items when no matches"() {
        expect:
        assertDoesNotMatch("name")
        assertDoesNotMatch("name", "other")
        assertDoesNotMatch("name", "na")
        assertDoesNotMatch("sN", "otherName")
        assertDoesNotMatch("sA", "someThing")
        assertDoesNotMatch("soN", "saN")
        assertDoesNotMatch("soN", "saName")
    }

    def "does not select items when multiple camel case matches"() {
        expect:
        matcher.find("sN", toList("someName", "soNa", "other")) == null
    }

    def "does not select items when multiple case insensitive matches"() {
        expect:
        matcher.find("someName", toList("somename", "SomeName", "other")) == null
        matcher.getMatches() == ["somename", "SomeName"] as Set
    }

    def "empty pattern does not select anything"() {
        expect:
        assertDoesNotMatch("", "something")
    }

    def "escapes regexp chars"() {
        expect:
        assertDoesNotMatch("name\\othername", "other")
    }

    def "reports potential matches"() {
        expect:
        matcher.find("name", toList("tame", "lame", "other")) == null
        matcher.getMatches().empty // TODO convert all getters to accessors
        matcher.getCandidates"() == ["tame", "lame"] as Set
    }

    def "does not select map entry when no matches"() {
        expect:
        matcher.find("soNa", singletonMap("does not match", 9)) == null
    }

    def "selects map entry when exact match"() {
        expect:
        matcher.find("name", singletonMap("name", 9)) == 9
    }

    def "selects map entry when one partial match"() {
        expect:
        matcher.find("soNa", singletonMap("someName", 9)) == 9
    }

    def "does not select map entry when multiple partial matches"() {
        expect:
        Map<String, Integer> items = Cast.uncheckedNonnullCast(GUtil.map("someName", 9, "soName", 10))
        matcher.find("soNa", items) == null
    }

    def "builds error message for no matches"() {
        setup:
        matcher.find("name", toList("other"))

        expect: // TODO remove assertThat everywhere
        matcher.formatErrorMessage("thing", "container") == "Thing 'name' not found in container."
    }

    def "builds error message for multiple matches"() {
        setup:
        matcher.find("n", toList("number", "name", "other"))

        expect:
        matcher.formatErrorMessage("thing", "container") == "Thing 'n' is ambiguous in container. Candidates are: 'name', 'number'."
    }

    def "builds error message for potential matches"() {
        setup:
        matcher.find("name", toList("other", "lame", "tame"))

        expect:
        matcher.formatErrorMessage("thing", "container") == "Thing 'name' not found in container. Some candidates are: 'lame', 'tame'."
    }

    /*TODO rename to matches() */
    def assertMatches(String name, String match, String... extraItems) {
        List<String> allItems = newArrayList(concat(toList(match), toList(extraItems))) // TODO make groovier
        matcher.find(name, allItems) == match && matcher.getMatches() == [match] as Set
    }

    def assertDoesNotMatch(String name, String... items) {
        matcher.find(name, toList(items)) == null && matcher.matches.empty
    }
}
