/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.test.fixtures


import com.google.common.collect.ImmutableSet
import com.google.common.collect.Range
import org.gradle.util.internal.VersionNumber

/**
 * Provides utility methods for filtering versions for "Coverage" classes.
 */
class VersionCoverage {
    static Set<String> versionsAtLeast(Collection<String> versionsToFilter, String fromVersion) {
        filterVersions(versionsToFilter, Range.atLeast(VersionNumber.parse(fromVersion)))
    }

    static Set<String> versionsAtMost(Collection<String> versionsToFilter, String toVersion) {
        filterVersions(versionsToFilter, Range.atMost(VersionNumber.parse(toVersion)))
    }

    static Set<String> versionsAbove(Collection<String> versionsToFilter, String fromVersion) {
        filterVersions(versionsToFilter, Range.greaterThan(VersionNumber.parse(fromVersion)))
    }

    static Set<String> versionsBelow(Collection<String> versionsToFilter, String toVersion) {
        filterVersions(versionsToFilter, Range.lessThan(VersionNumber.parse(toVersion)))
    }


    /**
     * Filters the given versions to those that are between the given closed bounds.
     *
     * @param versionsToFilter the versions to filter
     * @param from the lower bound, inclusive
     * @param to the upper bound, inclusive
     */
    static Set<String> versionsBetweenInclusive(Collection<String> versionsToFilter, String from, String to) {
        return filterVersions(versionsToFilter, Range.closed(VersionNumber.parse(from), VersionNumber.parse(to)))
    }

    /**
     * Filters the given versions to those that are between the given closed-open bounds. This is useful for semver-range-like behavior.
     *
     * @param versionsToFilter the versions to filter
     * @param from the lower bound, inclusive
     * @param to the upper bound, exclusive
     */
    static Set<String> versionsBetweenExclusive(Collection<String> versionsToFilter, String from, String to) {
        return filterVersions(versionsToFilter, Range.closedOpen(VersionNumber.parse(from), VersionNumber.parse(to)))
    }

    static Set<String> filterVersions(Collection<String> versionsToFilter, Range<VersionNumber> range) {
        versionsToFilter.stream().filter { range.contains(VersionNumber.parse(it)) }.collect(ImmutableSet.toImmutableSet())
    }
}
