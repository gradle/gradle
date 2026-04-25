/*
 * Copyright 2025 the original author or authors.
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

import com.google.common.base.Strings
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ArbitrarySupplier
import net.jqwik.api.Data
import net.jqwik.api.ForAll
import net.jqwik.api.From
import net.jqwik.api.FromData
import net.jqwik.api.Property
import net.jqwik.api.Table
import net.jqwik.api.Tuple
import net.jqwik.api.constraints.IntRange
import net.jqwik.api.constraints.Positive
import org.gradle.util.Matchers
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.both
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.nullValue

class VersionNumberTest {
    private static Matcher<VersionNumber> isVersion(int major, int minor, int micro, String qualifier) {
        return isVersion(major, minor, micro, 0, qualifier)
    }

    private static Matcher<VersionNumber> isVersion(int major, int minor, int micro, int patch, String qualifier) {
        // Check full equality:
        return both(Matchers.strictlyEqual(new VersionNumber(major, minor, micro, patch, qualifier)))
            // Check accessors:
            .and(new BaseMatcher<VersionNumber>() {
                @Override
                boolean matches(Object item) {
                    if (!(item instanceof VersionNumber)) {
                        return false
                    }
                    def version = (VersionNumber) item
                    return version.major == major
                        && version.minor == minor
                        && version.micro == micro
                        && version.patch == patch
                        && version.qualifier == qualifier
                }

                @Override
                void describeTo(Description description) {
                    description.appendText("version with major=").appendValue(major)
                        .appendText(", minor=").appendValue(minor)
                        .appendText(", micro=").appendValue(micro)
                        .appendText(", patch=").appendValue(patch)
                        .appendText(", qualifier=").appendValue(qualifier)
                }
            })
    }

    @Property
    def "construction"(@ForAll int major, @ForAll int minor) {
        assertThat(VersionNumber.version(major), isVersion(major, 0, 0, null))
        assertThat(VersionNumber.version(major, minor), isVersion(major, minor, 0, null))
    }

    @Property
    def "parsing"(
        @ForAll @Positive int major,
        @ForAll @Positive int minor,
        @ForAll @Positive int micro,
        @ForAll @Positive int patch
    ) {
        assertThat(
            "just major", VersionNumber.parse("$major"),
            isVersion(major, 0, 0, null),
        )
        assertThat(
            "major and minor", VersionNumber.parse("$major.$minor"),
            isVersion(major, minor, 0, null),
        )

        def fullVersion = "$major.$minor.$micro"
        assertThat(
            "major, minor and micro", VersionNumber.parse(fullVersion),
            isVersion(major, minor, micro, null),
        )
        assertThat(
            "major, minor and micro, doubled", VersionNumber.parse(fullVersion + "." + fullVersion),
            isVersion(major, minor, micro, fullVersion),
        )
        // Actually parsed as a qualifier in default scheme
        assertThat(
            "major, minor, micro and patch-as-qualifier", VersionNumber.parse("$major.$minor.$micro.$patch"),
            isVersion(major, minor, micro, patch.toString()),
        )
    }

    final class RestrictedQualifier implements ArbitrarySupplier<String> {
        @Override
        Arbitrary<String> get() {
            def typicalValues = Arbitraries.strings().ofMinLength(1).ofMaxLength(20)
                .excludeChars('.' as char)
                // Exclude any digits
                .filter { !it.codePoints().anyMatch(Character::isDigit) }
                .filter { !it.isBlank() }
            return MoreArbitraries.includeExtraEdgeCases(
                typicalValues,
                "SNAPSHOT",
                "rc1-SNAPSHOT"
            )
        }
    }

    final class NullableRestrictedQualifier implements ArbitrarySupplier<String> {
        @Override
        Arbitrary<String> get() {
            return new RestrictedQualifier().get().injectNull(0.25)
        }
    }

    @Property
    def "parsing with qualifier"(
        @ForAll @Positive int major,
        @ForAll @Positive int minor,
        @ForAll @Positive int micro,
        @ForAll @From(supplier = RestrictedQualifier) String qualifier
    ) {
        assertThat(
            "just major", VersionNumber.parse("$major-$qualifier"),
            isVersion(major, 0, 0, qualifier),
        )
        assertThat(
            "just major, with '.'", VersionNumber.parse("$major.$qualifier"),
            isVersion(major, 0, 0, qualifier),
        )
        assertThat(
            "major and minor", VersionNumber.parse("$major.$minor-$qualifier"),
            isVersion(major, minor, 0, qualifier),
        )
        assertThat(
            "major and minor, with '.'", VersionNumber.parse("$major.$minor.$qualifier"),
            isVersion(major, minor, 0, qualifier),
        )
        assertThat(
            "major, minor and micro", VersionNumber.parse("$major.$minor.$micro-$qualifier"),
            isVersion(major, minor, micro, qualifier),
        )
        assertThat(
            "major, minor and micro, with '.'", VersionNumber.parse("$major.$minor.$micro.$qualifier"),
            isVersion(major, minor, micro, qualifier),
        )
    }

    @Property
    def "parsing using withPatchNumber"(
        @ForAll @Positive int major,
        @ForAll @Positive int minor,
        @ForAll @Positive int micro,
        @ForAll @Positive int patch
    ) {
        assertThat(
            "just major", VersionNumber.withPatchNumber().parse("$major"),
            isVersion(major, 0, 0, 0, null),
        )
        assertThat(
            "major and minor", VersionNumber.withPatchNumber().parse("$major.$minor"),
            isVersion(major, minor, 0, 0, null),
        )
        assertThat(
            "major, minor and micro", VersionNumber.withPatchNumber().parse("$major.$minor.$micro"),
            isVersion(major, minor, micro, 0, null),
        )

        def fullVersion = "$major.$minor.$micro.$patch"
        assertThat(
            "major, minor, micro and patch", VersionNumber.withPatchNumber().parse(fullVersion),
            isVersion(major, minor, micro, patch, null),
        )
        assertThat(
            "major, minor, micro and patch, doubled",
            VersionNumber.withPatchNumber().parse(fullVersion + "." + fullVersion),
            isVersion(major, minor, micro, patch, fullVersion),
        )
    }

    @Property
    def "parsing with qualifier using withPatchNumber"(
        @ForAll @Positive int major,
        @ForAll @Positive int minor,
        @ForAll @Positive int micro,
        @ForAll @Positive int patch,
        @ForAll @From(supplier = RestrictedQualifier) String qualifier
    ) {
        assertThat(
            "just major", VersionNumber.withPatchNumber().parse("$major-$qualifier"),
            isVersion(major, 0, 0, 0, qualifier),
        )
        assertThat(
            "just major, with '.'", VersionNumber.withPatchNumber().parse("$major.$qualifier"),
            isVersion(major, 0, 0, 0, qualifier),
        )
        assertThat(
            "major and minor", VersionNumber.withPatchNumber().parse("$major.$minor-$qualifier"),
            isVersion(major, minor, 0, 0, qualifier),
        )
        assertThat(
            "major and minor, with '.'", VersionNumber.withPatchNumber().parse("$major.$minor.$qualifier"),
            isVersion(major, minor, 0, 0, qualifier),
        )
        assertThat(
            "major, minor and micro", VersionNumber.withPatchNumber().parse("$major.$minor.$micro-$qualifier"),
            isVersion(major, minor, micro, 0, qualifier),
        )
        assertThat(
            "major, minor and micro, with '.'", VersionNumber.withPatchNumber().parse("$major.$minor.$micro.$qualifier"),
            isVersion(major, minor, micro, 0, qualifier),
        )
        assertThat(
            "major, minor, micro and patch", VersionNumber.withPatchNumber().parse("$major.$minor.$micro.$patch-$qualifier"),
            isVersion(major, minor, micro, patch, qualifier),
        )
        assertThat(
            "major, minor, micro and patch with '.'", VersionNumber.withPatchNumber().parse("$major.$minor.$micro.$patch.$qualifier"),
            isVersion(major, minor, micro, patch, qualifier),
        )
    }

    @Data
    Iterable<Tuple.Tuple1<String>> unparseableStrings() {
        return Table.of(
            Tuple.of((String) null),
            Tuple.of(""),
            Tuple.of("foo"),
            Tuple.of("1."),
            Tuple.of("1.2.3-"),
            Tuple.of("."),
            Tuple.of("_"),
            Tuple.of("-"),
            Tuple.of(".1"),
            Tuple.of("a.1"),
            Tuple.of("1_2"),
            Tuple.of("1_2_2"),
            Tuple.of("1.2.3_4"),
        )
    }

    @Property
    @FromData("unparseableStrings")
    def "unparseable input returns UNKNOWN"(@ForAll String s) {
        assertThat(VersionNumber.parse(s), equalTo(VersionNumber.UNKNOWN))
    }

    @Property
    def "toString roundtrip"(
        @ForAll @Positive int major,
        @ForAll @Positive int minor,
        @ForAll @Positive int micro,
        @ForAll @Positive int patch,
        @ForAll @From(supplier = RestrictedQualifier) String qualifier
    ) {
        assertThat(VersionNumber.parse("$major").toString(), equalTo("${major}.0.0".toString()))
        assertThat(VersionNumber.parse("$major.$minor").toString(), equalTo("${major}.${minor}.0".toString()))
        assertThat(VersionNumber.parse("$major.$minor.$micro").toString(), equalTo("${major}.${minor}.${micro}".toString()))
        // patch is parsed as a qualifier in the default scheme
        assertThat(VersionNumber.parse("$major.$minor.$micro.$patch").toString(), equalTo("${major}.${minor}.${micro}-${patch}".toString()))
        assertThat(VersionNumber.parse("$major-$qualifier").toString(), equalTo("${major}.0.0-${qualifier}".toString()))
        assertThat(VersionNumber.parse("$major.$minor.$micro-$qualifier").toString(), equalTo("${major}.${minor}.${micro}-${qualifier}".toString()))
        assertThat(VersionNumber.parse("$major.$minor.$micro.$qualifier").toString(), equalTo("${major}.${minor}.${micro}-${qualifier}".toString()))
    }

    @Property
    def "toString roundtrip using withPatchNumber"(
        @ForAll @Positive int major,
        @ForAll @Positive int minor,
        @ForAll @Positive int micro,
        @ForAll @Positive int patch,
        @ForAll @From(supplier = RestrictedQualifier) String qualifier
    ) {
        def patchScheme = VersionNumber.withPatchNumber()
        assertThat(patchScheme.parse("$major").toString(), equalTo("${major}.0.0.0".toString()))
        assertThat(patchScheme.parse("$major.$minor").toString(), equalTo("${major}.${minor}.0.0".toString()))
        assertThat(patchScheme.parse("$major.$minor.$micro").toString(), equalTo("${major}.${minor}.${micro}.0".toString()))
        assertThat(patchScheme.parse("$major.$minor.$micro.$patch").toString(), equalTo("${major}.${minor}.${micro}.${patch}".toString()))
        assertThat(patchScheme.parse("$major.$minor-$qualifier").toString(), equalTo("${major}.${minor}.0.0-${qualifier}".toString()))
        assertThat(patchScheme.parse("$major.$minor.$micro.$patch-$qualifier").toString(), equalTo("${major}.${minor}.${micro}.${patch}-${qualifier}".toString()))
        assertThat(patchScheme.parse("$major.$minor.$micro.$patch.$qualifier").toString(), equalTo("${major}.${minor}.${micro}.${patch}-${qualifier}".toString()))
    }

    @Property
    def "equality"(
        @ForAll @IntRange(min = 1) int major,
        @ForAll @IntRange(min = 1) int minor,
        @ForAll @IntRange(min = 1) int micro,
        @ForAll @IntRange(min = 1) int patch,
        @ForAll @From(supplier = NullableRestrictedQualifier) String qualifier
    ) {
        def version = new VersionNumber(major, minor, micro, patch, qualifier)
        assertThat(new VersionNumber(major, minor, micro, patch, qualifier), Matchers.strictlyEqual(version))

        assert new VersionNumber(major - 1, minor, micro, patch, qualifier) != version
        assert new VersionNumber(major, minor - 1, micro, patch, qualifier) != version
        assert new VersionNumber(major, minor, micro - 1, patch, qualifier) != version
        assert new VersionNumber(major, minor, micro, patch - 1, qualifier) != version

        def differentQualifier = Strings.nullToEmpty(qualifier) + "x"
        assert new VersionNumber(major, minor, micro, patch, differentQualifier) != version
    }

    @Property
    def "comparison - numeric ordering"(
        @ForAll @IntRange(min = 1) int major,
        @ForAll @IntRange(min = 1) int minor,
        @ForAll @IntRange(min = 1) int micro,
        @ForAll @IntRange(min = 1) int patch,
        @ForAll @From(supplier = NullableRestrictedQualifier) String qualifier
    ) {
        def version = new VersionNumber(major, minor, micro, patch, qualifier)
        assertThat(new VersionNumber(major, minor, micro, patch, qualifier), Matchers.strictlyComparesEqual(version))

        // 3-arg (default scheme, patch defaults to 0) and 4-arg with explicit patch=0 compare equal.
        assertThat(
            new VersionNumber(major, minor, micro, qualifier),
            Matchers.strictlyComparesEqual(new VersionNumber(major, minor, micro, 0, qualifier))
        )

        assertThat(new VersionNumber(major - 1, minor, micro, patch, qualifier), Matchers.strictlyLessThan(version))
        assertThat(new VersionNumber(major, minor - 1, micro, patch, qualifier), Matchers.strictlyLessThan(version))
        assertThat(new VersionNumber(major, minor, micro - 1, patch, qualifier), Matchers.strictlyLessThan(version))
        assertThat(new VersionNumber(major, minor, micro, patch - 1, qualifier), Matchers.strictlyLessThan(version))
    }

    @Property
    def "comparison - qualifier ordering"(
        @ForAll @Positive int major,
        @ForAll @Positive int minor,
        @ForAll @Positive int micro,
        @ForAll @Positive int patch,
        @ForAll @From(supplier = RestrictedQualifier) String qualifier
    ) {
        def qualified = new VersionNumber(major, minor, micro, patch, qualifier)
        def unqualified = new VersionNumber(major, minor, micro, patch, null)
        assertThat(qualified, Matchers.strictlyLessThan(unqualified))

        // Case-insensitive lexicographic ordering: "BETA" vs "alpha" would compare negative
        // under raw ASCII (B=66 < a=97) but positive after lowercasing (b > a).
        assertThat(
            new VersionNumber(major, minor, micro, patch, "BETA"),
            Matchers.strictlyGreaterThan(new VersionNumber(major, minor, micro, patch, "alpha"))
        )
        assertThat(
            new VersionNumber(major, minor, micro, patch, "RELEASE"),
            Matchers.strictlyGreaterThan(new VersionNumber(major, minor, micro, patch, "beta"))
        )
    }

    @Property
    def "base version"(
        @ForAll @Positive int major,
        @ForAll @Positive int minor,
        @ForAll @Positive int micro,
        @ForAll @Positive int patch,
        @ForAll @From(supplier = NullableRestrictedQualifier) String qualifier
    ) {
        def base = new VersionNumber(major, minor, micro, patch, qualifier).baseVersion
        assertThat(base, isVersion(major, minor, micro, patch, null))
        assertThat(base.qualifier, nullValue())
    }
}

