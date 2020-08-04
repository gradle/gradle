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
import org.junit.Test
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

    /*TODO remove Test*/
    def /*TODO remove public*/ /*TODO convert void to test*/ selectsExactMatch() /*TODO rename to full sentences*/ {
        expect:
        assertMatches("name", "name"); /*TODO remove semicolon*/ /*TODO use single quotes where possible*/
        assertMatches("name", "name", "other");
    }

    @Test  /*TODO convert to parameterized test */
    public void selectsItemWithMatchingPrefix() {
        expect:
        assertMatches("na", "name");
        assertMatches("na", "name", "other");
        // Mixed case
        assertMatches("na", "Name");
        assertMatches("NA", "name");
        assertMatches("somena", "someName");
        assertMatches("somena", "SomeName");
        assertMatches("somena", "SomeName");
        assertMatches("some na", "Some Name");
    }

    @Test
    public void selectsItemWithMatchingCamelCasePrefix() {
        expect:
        assertMatches("sN", "someName");
        assertMatches("soN", "someName");
        assertMatches("SN", "someName");
        assertMatches("SN", "SomeName");
        assertMatches("SN", "SomeNameWithExtraStuff");
        assertMatches("so_n", "some_name");
        assertMatches("so_n", "some_Name");
        assertMatches("so_n_wi_ext", "some_Name_with_EXTRA");
        assertMatches("so.n", "some.name");
        assertMatches("so n", "some name");
        assertMatches("ABC", "ABC");
        assertMatches("a9N", "a9Name");
        assertMatches("a9N", "abc9Name");
        assertMatches("a9n", "abc9Name");
    }

    @Test
    public void prefersExactMatchOverCaseInsensitiveMatch() {
        expect:
        assertMatches("name", "name", "Name", "NAME");
        assertMatches("someName", "someName", "SomeName", "somename", "SOMENAME");
        assertMatches("some Name", "some Name", "Some Name", "some name", "SOME NAME");
    }

    @Test
    public void prefersExactMatchOverPartialMatch() {
        expect:
        assertMatches("name", "name", "nam", "n", "NAM");
    }

    @Test
    public void prefersExactMatchOverPrefixMatch() {
        expect:
        assertMatches("someName", "someName", "someNameWithExtra");
    }

    @Test
    public void prefersExactMatchOverCamelCaseMatch() {
        expect:
        assertMatches("sName", "sName", "someName", "sNames");
        assertMatches("so Name", "so Name", "some Name", "so name");
        assertMatches("ABC", "ABC", "AaBbCc");
    }

    @Test
    public void prefersFullCamelCaseMatchOverCamelCasePrefix() {
        expect:
        assertMatches("sN", "someName", "someNameWithExtra");
        assertMatches("name", "names", "nameWithExtra");
        assertMatches("s_n", "some_name", "some_name_with_extra");
    }

    @Test
    public void prefersCaseSensitiveCamelCaseMatchOverCaseInsensitiveCamelCaseMatch() {
        expect:
        assertMatches("soNa", "someName", "somename");
        assertMatches("SN", "SomeName", "someName");
        assertMatches("na1", "name1", "Name1", "NAME1");
    }

    @Test
    public void prefersCaseInsensitiveMatchOverCamelCaseMatch() {
        expect:
        assertMatches("somename", "someName", "someNameWithExtra");
        assertMatches("soNa", "sona", "someName");
    }

    @Test
    public void doesNotSelectItemsWhenNoMatches() {
        expect:
        assertDoesNotMatch("name");
        assertDoesNotMatch("name", "other");
        assertDoesNotMatch("name", "na");
        assertDoesNotMatch("sN", "otherName");
        assertDoesNotMatch("sA", "someThing");
        assertDoesNotMatch("soN", "saN");
        assertDoesNotMatch("soN", "saName");
    }

    @Test
    public void doesNotSelectItemsWhenMultipleCamelCaseMatches() {
        expect:
        matcher.find("sN", toList("someName", "soNa", "other")) == null

    }

    @Test
    public void doesNotSelectItemsWhenMultipleCaseInsensitiveMatches() {
        expect:
        matcher.find("someName", toList("somename", "SomeName", "other")) == null
        matcher.getMatches() == ["somename", "SomeName"] as Set
    }

    @Test
    public void emptyPatternDoesNotSelectAnything() {
        expect:
        assertDoesNotMatch("", "something");
    }

    @Test
    public void escapesRegexpChars() {
        expect:
        assertDoesNotMatch("name\\othername", "other");
    }

    @Test
    public void reportsPotentialMatches() {
        expect:
        matcher.find("name", toList("tame", "lame", "other")) == null;
        matcher.getMatches().empty // TODO convert all getters to accessors
        matcher.getCandidates() == ["tame", "lame"] as Set
    }

    @Test
    public void doesNotSelectMapEntryWhenNoMatches() {
        expect:
        matcher.find("soNa", singletonMap("does not match", 9)) == null
    }

    @Test
    public void selectsMapEntryWhenExactMatch() {
        expect:
        matcher.find("name", singletonMap("name", 9)) == 9
    }

    @Test
    public void selectsMapEntryWhenOnePartialMatch() {
        expect:
        matcher.find("soNa", singletonMap("someName", 9)) == 9
    }

    @Test
    public void doesNotSelectMapEntryWhenMultiplePartialMatches() {
        expect:
        Map<String, Integer> items = Cast.uncheckedNonnullCast(GUtil.map("someName", 9, "soName", 10));
        matcher.find("soNa", items) == null
    }

    @Test
    public void buildsErrorMessageForNoMatches() {
        setup:
        matcher.find("name", toList("other"));

        expect: // TODO remove assertThat everywhere
        matcher.formatErrorMessage("thing", "container") == "Thing 'name' not found in container."
    }

    @Test
    public void buildsErrorMessageForMultipleMatches() {
        setup:
        matcher.find("n", toList("number", "name", "other"));

        expect:
        matcher.formatErrorMessage("thing", "container") == "Thing 'n' is ambiguous in container. Candidates are: 'name', 'number'."
    }

    @Test
    public void buildsErrorMessageForPotentialMatches() {
        setup:
        matcher.find("name", toList("other", "lame", "tame"));

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
