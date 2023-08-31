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


import org.gradle.util.internal.VersionNumber

import javax.annotation.Nullable

/**
 * Provides utility methods for filtering versions for "Coverage" classes.
 */
class VersionCoverage {
    static Set<String> versionsAtLeast(Collection<String> versionsToFilter, String fromVersion) {
        versionsBetweenInclusive(versionsToFilter, fromVersion, null)
    }

    static Set<String> versionsAtMost(Collection<String> versionsToFilter, String toVersion) {
        versionsBetweenInclusive(versionsToFilter, null, toVersion)
    }

    static Set<String> versionsAbove(Collection<String> versionsToFilter, String fromVersion) {
        versionsBetweenExclusive(versionsToFilter, fromVersion, null)
    }

    static Set<String> versionsBelow(Collection<String> versionsToFilter, String toVersion) {
        versionsBetweenExclusive(versionsToFilter, null, toVersion)
    }

    static Set<String> versionsBetweenInclusive(Collection<String> versionsToFilter, @Nullable String from, @Nullable String to) {
        return filterVersions(versionsToFilter, from, to) { version, fromVersion, toVersion ->
            return (fromVersion == null || fromVersion <= version) && (toVersion == null || version <= toVersion)
        }
    }

    /**
     * Like [versionsBetweenInclusive], but excludes the `to`. `from` is still included. This is useful for semver-range-like behavior.
     */
    static Set<String> versionsBetweenRange(Collection<String> versionsToFilter, @Nullable String from, @Nullable String to) {
        return filterVersions(versionsToFilter, from, to) { version, fromVersion, toVersion ->
            return (fromVersion == null || fromVersion < version) && (toVersion == null || version < toVersion)
        }
    }

    private static Set<String> versionsBetweenExclusive(Collection<String> versionsToFilter, @Nullable String from, @Nullable String to) {
        return filterVersions(versionsToFilter, from, to) { version, fromVersion, toVersion ->
            return (fromVersion == null || fromVersion < version) && (toVersion == null || version < toVersion)
        }
    }

    static Set<String> filterVersions(Collection<String> versionsToFilter, @Nullable String lowerBound, @Nullable String upperBound, Closure filter) {
        def fromVersion = lowerBound == null ? null : VersionNumber.parse(lowerBound)
        def toVersion = upperBound == null ? null : VersionNumber.parse(upperBound)
        versionsToFilter.findAll { filter(VersionNumber.parse(it), fromVersion, toVersion) }.toSet().asImmutable()
    }
}
