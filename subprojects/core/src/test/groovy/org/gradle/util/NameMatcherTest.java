/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.singletonMap;
import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.toList;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class NameMatcherTest {
    private final NameMatcher matcher = new NameMatcher();

    @Test
    public void selectsExactMatch() {
        assertMatches("name", "name");
        assertMatches("name", "name", "other");
    }

    @Test
    public void selectsItemWithMatchingPrefix() {
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
        assertMatches("name", "name", "Name", "NAME");
        assertMatches("someName", "someName", "SomeName", "somename", "SOMENAME");
        assertMatches("some Name", "some Name", "Some Name", "some name", "SOME NAME");
    }

    @Test
    public void prefersExactMatchOverPartialMatch() {
        assertMatches("name", "name", "nam", "n", "NAM");
    }

    @Test
    public void prefersExactMatchOverPrefixMatch() {
        assertMatches("someName", "someName", "someNameWithExtra");
    }

    @Test
    public void prefersExactMatchOverCamelCaseMatch() {
        assertMatches("sName", "sName", "someName", "sNames");
        assertMatches("so Name", "so Name", "some Name", "so name");
        assertMatches("ABC", "ABC", "AaBbCc");
    }

    @Test
    public void prefersFullCamelCaseMatchOverCamelCasePrefix() {
        assertMatches("sN", "someName", "someNameWithExtra");
        assertMatches("name", "names", "nameWithExtra");
        assertMatches("s_n", "some_name", "some_name_with_extra");
    }

    @Test
    public void prefersCaseSensitiveCamelCaseMatchOverCaseInsensitiveCamelCaseMatch() {
        assertMatches("soNa", "someName", "somename");
        assertMatches("SN", "SomeName", "someName");
        assertMatches("na1", "name1", "Name1", "NAME1");
    }

    @Test
    public void prefersCaseInsensitiveMatchOverCamelCaseMatch() {
        assertMatches("somename", "someName", "someNameWithExtra");
        assertMatches("soNa", "sona", "someName");
    }

    @Test
    public void doesNotSelectItemsWhenNoMatches() {
        assertDoesNotMatch("name");
        assertDoesNotMatch("name", "other");
        assertDoesNotMatch("name", "na");
        assertDoesNotMatch("sN", "otherName");
        assertDoesNotMatch("sN", "someThing");
        assertDoesNotMatch("soN", "saN");
        assertDoesNotMatch("soN", "saName");
    }

    @Test
    public void doesNotSelectItemsWhenMultipleCamelCaseMatches() {
        assertThat(matcher.find("sN", toList("someName", "soNa", "other")), nullValue());
        assertThat(matcher.getMatches(), equalTo(toSet("someName", "soNa")));
    }

    @Test
    public void doesNotSelectItemsWhenMultipleCaseInsensitiveMatches() {
        assertThat(matcher.find("someName", toList("somename", "SomeName", "other")), nullValue());
        assertThat(matcher.getMatches(), equalTo(toSet("somename", "SomeName")));
    }

    @Test
    public void emptyPatternDoesNotSelectAnything() {
        assertDoesNotMatch("", "something");
    }

    @Test
    public void escapesRegexpChars() {
        assertDoesNotMatch("name\\othername", "other");
    }

    @Test
    public void reportsPotentialMatches() {
        assertThat(matcher.find("name", toList("tame", "lame", "other")), nullValue());
        assertThat(matcher.getMatches(), isEmpty());
        assertThat(matcher.getCandidates(), equalTo(toSet("tame", "lame")));
    }

    @Test
    public void doesNotSelectMapEntryWhenNoMatches() {
        Integer match = matcher.find("soNa", singletonMap("does not match", 9));
        assertThat(match, nullValue());
    }

    @Test
    public void selectsMapEntryWhenExactMatch() {
        Integer match = matcher.find("name", singletonMap("name", 9));
        assertThat(match, equalTo(9));
    }

    @Test
    public void selectsMapEntryWhenOnePartialMatch() {
        Integer match = matcher.find("soNa", singletonMap("someName", 9));
        assertThat(match, equalTo(9));
    }

    @Test
    public void doesNotSelectMapEntryWhenMultiplePartialMatches() {
        Map<String, Integer> items = GUtil.map("someName", 9, "soName", 10);
        Integer match = matcher.find("soNa", items);
        assertThat(match, nullValue());
    }

    @Test
    public void buildsErrorMessageForNoMatches() {
        matcher.find("name", toList("other"));
        assertThat(matcher.formatErrorMessage("thing", "container"), equalTo("Thing 'name' not found in container."));
    }

    @Test
    public void buildsErrorMessageForMultipleMatches() {
        matcher.find("n", toList("number", "name", "other"));
        assertThat(matcher.formatErrorMessage("thing", "container"), equalTo("Thing 'n' is ambiguous in container. Candidates are: 'name', 'number'."));
    }

    @Test
    public void buildsErrorMessageForPotentialMatches() {
        matcher.find("name", toList("other", "lame", "tame"));
        assertThat(matcher.formatErrorMessage("thing", "container"), equalTo("Thing 'name' not found in container. Some candidates are: 'lame', 'tame'."));
    }

    private void assertDoesNotMatch(String name, String... items) {
        assertThat(matcher.find(name, toList(items)), nullValue());
        assertThat(matcher.getMatches(), isEmpty());
    }

    private void assertMatches(String name, String match, String... extraItems) {
        List<String> allItems = newArrayList(concat(toList(match), toList(extraItems)));
        assertThat(matcher.find(name, allItems), equalTo(match));
        assertThat(matcher.getMatches(), equalTo(toSet(match)));
    }
}
