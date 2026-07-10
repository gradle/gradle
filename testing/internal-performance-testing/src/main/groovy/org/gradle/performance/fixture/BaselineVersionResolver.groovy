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

package org.gradle.performance.fixture

import com.google.common.base.Splitter
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.versions.PublishedVersionDeterminer
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.performance.results.ResultsStoreHelper
import org.gradle.util.GradleVersion
import org.junit.Assume

import java.util.regex.Pattern

class BaselineVersionResolver {

    private static final Pattern COMMA_OR_SEMICOLON = Pattern.compile('[;,]')

    static Iterable<String> toBaselineVersions(ReleasedVersionDistributions releases, List<String> targetVersions, String minimumBaseVersion) {
        def overrideBaselinesProperty = System.getProperty('org.gradle.performance.baselines')
        def versions = resolveBaselineVersions(overrideBaselinesProperty, targetVersions)

        LinkedHashSet<String> resolvedVersions = versions.collect { resolveVersion(it, releases) } as LinkedHashSet<String>

        if (resolvedVersions.isEmpty() || addMostRecentRelease(overrideBaselinesProperty, versions)) {
            // Always include the most recent final release if we're not testing against a nightly or a snapshot
            resolvedVersions.add(releases.mostRecentRelease.version.version)
        }

        resolvedVersions.removeAll { !versionMeetsLowerBaseVersionRequirement(it, minimumBaseVersion) }

        if (resolvedVersions.isEmpty()) {
            Assume.assumeFalse("Ignore the test if all baseline versions are filtered out in Historical Performance Test", ResultsStoreHelper.isHistoricalChannel())
        }

        assert !resolvedVersions.isEmpty(): "No versions selected: ${versions}"

        resolvedVersions
    }

    private static List<String> resolveBaselineVersions(String overrideBaselinesProperty, List<String> targetVersions) {
        List<String> versions
        if (overrideBaselinesProperty) {
            versions = resolveOverriddenVersions(overrideBaselinesProperty, targetVersions)
        } else {
            versions = targetVersions
        }

        if (versions.contains("none")) {
            return []
        }

        versions
    }

    private static String resolveVersion(String version, ReleasedVersionDistributions releases) {
        switch (version) {
            case 'last':
                return releases.mostRecentRelease.version.version
            case 'nightly':
                return PublishedVersionDeterminer.latestNightlyVersion
            case 'defaults':
                throw new IllegalArgumentException("'defaults' shouldn't be used in target versions.")
            default:
                def releasedVersion = findRelease(releases, version)
                if (releasedVersion) {
                    return releasedVersion.version.version
                } else if (isRcVersionOrSnapshot(version)) {
                    // for snapshots, we don't have a cheap way to check if it really exists, so we'll just
                    // blindly add it to the list and trust the test author
                    // Only active rc versions are listed in all-released-versions.properties that ReleasedVersionDistributions uses
                    return version
                } else {
                    throw new RuntimeException("Cannot find Gradle release that matches version '$version'")
                }
        }
    }

    private static boolean addMostRecentRelease(String overrideBaselinesProperty, List<String> versions) {
        if (overrideBaselinesProperty) {
            return false
        }
        return !versions.any { it == 'last' || it == 'nightly' || isRcVersionOrSnapshot(it) }
    }

    private static boolean versionMeetsLowerBaseVersionRequirement(String targetVersion, String minimumBaseVersion) {
        return minimumBaseVersion == null || GradleVersion.version(targetVersion).baseVersion >= GradleVersion.version(minimumBaseVersion)
    }

    private static List<String> resolveOverriddenVersions(String overrideBaselinesProperty, List<String> targetVersions) {
        List<String> versions = Splitter.on(COMMA_OR_SEMICOLON)
                .omitEmptyStrings()
                .splitToList(overrideBaselinesProperty)

        versions.collectMany([] as Set) { version -> version == 'defaults' ? targetVersions : [version] } as List<String>
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private static boolean isRcVersionOrSnapshot(String version) {
        GradleVersion versionObject = GradleVersion.version(version)
        // there is no public API for checking for RC version, this is an internal way
        return versionObject.snapshot || versionObject.stage?.stage == 3
    }

    private static GradleDistribution findRelease(ReleasedVersionDistributions releases, String requested) {
        GradleDistribution best = null
        for (GradleDistribution release : releases.all) {
            if (release.version.version == requested) {
                return release
            }
            if (!release.version.snapshot && release.version.baseVersion.version == requested && (best == null || best.version < release.version)) {
                best = release
            }
        }

        best
    }
}
