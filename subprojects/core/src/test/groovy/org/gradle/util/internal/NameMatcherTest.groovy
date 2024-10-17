/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.util.internal

import spock.lang.Issue
import spock.lang.Specification

class NameMatcherTest extends Specification {

    NameMatcher matcher

    def setup() {
        matcher = new NameMatcher()
    }

    def "selects exact match"() {
        expect:
        matches("name", "name")
        matches("name", "name", ["other"])
    }

    def "selects item with matching prefix"() {
        expect:
        matches("na", "name")
        matches("na", "name", ["other"])

        and: // Mixed case
        matches("na", "Name")
        matches("NA", "name")
        matches("somena", "someName")
        matches("somena", "SomeName")
        matches("somena", "SomeName")
        matches("some na", "Some Name")
        matches("somenamew", "someNameWithExtra")
    }

    def "selects item with matching camel case prefix"() {
        expect:
        matches("sN", "someName")
        matches("soN", "someName")
        matches("SN", "someName")
        matches("SN", "SomeName")
        matches("SN", "SomeNameWithExtraStuff")
        matches("so_n", "some_name")
        matches("so_n", "some_Name")
        matches("so_n_wi_ext", "some_Name_with_EXTRA")
        matches("so.n", "some.name")
        matches("so n", "some name")
        matches("ABC", "ABC")
        matches("a9N", "a9Name")
        matches("a9N", "abc9Name")
        matches("a9n", "abc9Name")
    }

    def "selects item with matching kebab case prefix"() {
        expect:
        matches("sN", "some-name")
        matches("SN", "some-name")
        matches("SN", "some-name-with-extra-stuff")
        matches("a9N", "a9-name")
        matches("a9N", "abc9-name")
        matches("A9N", "abc9-name")
    }

    def "does not select kebab case with upper case chars"() {
        expect:
        doesNotMatch("sN", ["some-Name"])
        doesNotMatch("SN", ["some-Name"])
        doesNotMatch("a9N", ["a9-Name"])
        doesNotMatch("a9N", ["abc9-Name"])
        doesNotMatch("A9N", ["abc9-Name"])
    }

    def "prefers exact match over case insensitive match"() {
        expect:
        matches("name", "name", ["Name", "NAME"])
        matches("someName", "someName", ["SomeName", "somename", "SOMENAME"])
        matches("some Name", "some Name", ["Some Name", "some name", "SOME NAME"])
    }

    def "prefers exact match over partial match"() {
        expect:
        matches("name", "name", ["nam", "n", "NAM"])
    }

    @Issue("https://github.com/gradle/gradle/issues/1185")
    def "handles numbers as separators for camelCase"() {
        expect:
        ambiguous("unique", ["unique1", "uniqueA"])
        ambiguous("unique", ["unique1", "uniquea"])
        matches("unique", "unique", ["uniqueA"])
        matches("unique", "unique", ["unique2"])
        matches("unique", "unique", ["uniquea"])
        matches("uni", "unique", ["uniqueA"])
        ambiguous("uni", ["unique", "unique2"])
        ambiguous("uni", ["unique", "uniquea"])
    }

    def "prefers exact match over prefix match"() {
        expect:
        matches("someName", "someName", ["someNameWithExtra"])
    }

    def "prefers exact match over camel case match"() {
        expect:
        matches("sName", "sName", ["someName", "sNames"])
        matches("so Name", "so Name", ["some Name", "so name"])
        matches("ABC", "ABC", ["AaBbCc"])
    }

    def "prefers exact match over kebab case match"() {
        expect:
        matches("sName", "sName", ["some-name", "some-Name"])
    }

    def "prefers full camel case match over camel case prefix"() {
        expect:
        matches("sN", "someName", ["someNameWithExtra"])
        matches("name", "names", ["nameWithExtra"])
        matches("s_n", "some_name", ["some_name_with_extra"])
    }

    def "prefers full kebab case match over kebab case prefix"() {
        expect:
        matches("sN", "some-name", ["some-name-with-extra"])
        matches("name", "names", ["name-with-extra"])
    }

    def "prefers case sensitive camel case match over case insensitive camel case match"() {
        expect:
        matches("soNa", "someName", ["somename"])
        matches("SN", "SomeName", ["someName"])
        matches("na1", "name1", ["Name1", "NAME1"])
    }

    def "prefers case sensitive prefix match over case insensitive camelcase match"() {
        expect:
        matches("someNameWith", "someNameWithExtra", ["someNameOtherWithExtra"])
        matches("someNameWith", "someNameWithExtra", ["somenamewithextra"])
        matches("sNW", "someNameWithExtra", ["someNameOtherWithExtra"])
    }

    def "prefers case insensitive exact match over case sensitive prefix match"() {
        expect:
        matches("someNameWith", "somenamewith", ["someNameWithExtra"])
    }

    def "prefers sequential camel case match over non-sequential camel case match"() {
        expect:
        matches("sNW", "someNameWithExtra", ["someNameOtherWithExtra"])
    }

    def "prefers case insensitive match over camel case match"() {
        expect:
        matches("somename", "someName", ["someNameWithExtra"])
        matches("soNa", "sona", ["someName"])
    }

    @Issue("https://github.com/gradle/gradle/issues/23580")
    def "handles JDK bug with case insensitive match"() {
        expect:
        matches("publishReleaseBundle", "publishReleaseBundlePublicationToTempPublishRepository", ["publishReleaseObfuscatedBundlePublicationToTempPublishRepository"])
    }

    def "prefers kebab case match over case insensitive camel case match"() {
        expect:
        matches("sN", "some-name", ["sand"])
        matches("sN", "some-name-with", ["sand"])
        matches("sN", "some-name-with-extra", ["sand"])
    }

    def "does not select items when no matches"() {
        expect:
        doesNotMatch("name", [])
        doesNotMatch("name", ["other"])
        doesNotMatch("name", ["na"])
        doesNotMatch("sN", ["otherName"])
        doesNotMatch("sA", ["someThing"])
        doesNotMatch("soN", ["saN"])
        doesNotMatch("soN", ["saName"])
    }

    def "does not select items when multiple camel case matches"() {
        expect:
        ambiguous("sN", ["someName", "soNa", "other"], ["someName", "soNa"])
        ambiguous("sNE", ["someNameWithExtraStuff", "someNameWithOtherExtraStuff"])
    }

    def "does not select items when multiple kebab case matches"() {
        expect:
        ambiguous("sN", ["some-name", "some-number", "other"], ["some-name", "some-number"])
    }

    def "does not select items when multiple mixed camel and kebab case matches"() {
        expect:
        ambiguous("sN", ["some-name", "someName", "other"], ["some-name", "someName"])
    }

    def "does not select items when multiple case insensitive matches"() {
        expect:
        ambiguous("someName", ["somename", "SomeName", "other"], ["somename", "SomeName"])
    }

    def "empty pattern does not select anything"() {
        expect:
        doesNotMatch("", ["something"])
    }

    def "escapes regexp chars"() {
        expect:
        doesNotMatch("name\\othername", ["other"])
    }

    def "reports potential matches"() {
        expect:
        doesNotMatch("name", ["other", "lame", "tame"])
        matcher.candidates == ["tame", "lame"] as Set
    }

    def "reports potential matches ignoring case"() {
        expect:
        doesNotMatch("NAME", ["other", "lame", "tame"])
        matcher.candidates == ["tame", "lame"] as Set
    }

    def "does not select map entry when no matches"() {
        expect:
        matcher.find("soNa", ["does not match" : 9]) == null
    }

    def "selects map entry when exact match"() {
        expect:
        matcher.find("name", ["name" : 9]) == 9
    }

    def "selects map entry when one partial match"() {
        expect:
        matcher.find("soNa", ["someName" : 9]) == 9
    }

    def "does not select map entry when multiple partial matches"() {
        setup:
        Map items = ["someName" : 9, "soName" : 10]

        expect:
        matcher.find("soNa", items) == null
    }

    def "builds error message for no matches"() {
        setup:
        doesNotMatch("name", ["other"])

        expect:
        matcher.formatErrorMessage("thing", "container") == "thing 'name' not found in container."
    }

    def "builds error message for multiple matches"() {
        setup:
        ambiguous("n", ["number", "name", "other"], ["name", "number"])

        expect:
        matcher.formatErrorMessage("thing", "container") == "thing 'n' is ambiguous in container. Candidates are: 'name', 'number'."
    }

    def "builds error message for potential matches"() {
        setup:
        doesNotMatch("name", ["other", "lame", "tame"])

        expect:
        matcher.formatErrorMessage("thing", "container") == "thing 'name' not found in container. Some candidates are: 'lame', 'tame'."
    }

    def "handles non-English locale properly"() {
        expect:
        matches("დავალება", "დავალება")
        matches("BazIGörevler", "bazıgörevler")
        matches("či", "Čitač")
        matches("яЗ", "якесьЗавдання")
        matches("պԱ", "պատահական-առաջադրանք")
        matches("де", "дело", ["потеха"])
        matches("ТА", "тапсырма")
        matches("НӨ", "НэмэлтӨгөгдөлБүхийЗаримДаалгавар")
        matches("ΚΕ", "κάποια-εργασία-με-επιπλέον-δεδομένα")
    }

    def "gives suggestions in non-English locale properly"() {
        expect:
        doesNotMatch("StaSS", ["straße", "saße", "wasser"])
        matcher.candidates == ["saße", "straße"] as Set
        doesNotMatch("ДЕЛАЙ", ["сделай", "СДЕЛАЛ", "челка"])
        matcher.candidates == ["сделай", "СДЕЛАЛ"] as Set
    }

    def "handles emojis"() {
        expect:
        matches("✅", "✅")
        matches("✅", "✅", ["❌"])
        matches("✅", "✅💀")
        matches("✅", "✅whyyy💀")
    }

    def matches(String name, String match, Collection<String> extraItems = []) {
        assert matcher.find(name, ([match] + extraItems).shuffled()) == match
        return matcher.matches == [match] as Set
    }

    def doesNotMatch(String name, Collection<String> items) {
        assert matcher.matches.empty
        return matcher.find(name, items) == null
    }

    def ambiguous(String name, List<String> items, Collection<String> matches = items) {
        assert matcher.find(name, items) == null
        return matcher.matches == matches as Set
    }
}
