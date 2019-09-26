/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.Describable;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Represents a constraint that is used to match module versions to a dependency.
 * Each of {@link #getPreferredVersion()}, {@link #getRequiredVersion()} and {@link #getStrictVersion()} is represented by a version String,
 * that can be compared against a module version to determine if the version matches.
 *
 * <h4>Version syntax</h4>
 * <p>
 * Gradle supports different ways of declaring a version String:
 * <ul>
 *     <li>An exact version: e.g. 1.3, 1.3.0-beta3, 1.0-20150201.131010-1</li>
 *     <li>A Maven-style version range: e.g. [1.0,), [1.1, 2.0), (1.2, 1.5]
 *          <ul>
 *              <li>The '[' and ']' symbols indicate an inclusive bound; '(' and ')' indicate an exclusive bound.</li>
 *              <li>When the upper or lower bound is missing, the range has no upper or lower bound.</li>
 *              <li>The symbol ']' can be used instead of '(' for an exclusive lower bound, and '[' instead of ')' for exclusive upper bound. e.g "]1.0, 2.0["</li>
 *          </ul>
 *     </li>
 *     <li>A prefix version range: e.g. 1.+, 1.3.+
 *          <ul>
 *              <li>Only versions exactly matching the portion before the '+' are included.</li>
 *              <li>The range '+' on it's own will include any version.</li>
 *          </ul>
 *     </li>
 *     <li>A latest-status version: e.g. latest.integration, latest.release
 *         <ul>
 *             <li>Will match the highest versioned module with the specified status. See {@link ComponentMetadata#getStatus()}.</li>
 *         </ul>
 *     </li>
 *     <li>A Maven SNAPSHOT version identifier: e.g. 1.0-SNAPSHOT, 1.4.9-beta1-SNAPSHOT</li>
 * </ul>
 *
 * <h4>Version ordering</h4>
 *
 * Versions have an implicit ordering. Version ordering is used to:
 * <ul>
 *     <li>Determine if a particular version is included in a range.</li>
 *     <li>Determine which version is 'newest' when performing conflict resolution.</li>
 * </ul>
 *
 * <p>Versions are ordered based on the following rules:</p>
 *
 * <ul>
 * <li>Each version is split into it's constituent "parts":
 * <ul>
 *     <li>The characters [<code>. - _ +</code>] are used to separate the different "parts" of a version.</li>
 *     <li>Any part that contains both digits and letters is split into separate parts for each: `1a1 == 1.a.1`</li>
 *     <li>Only the parts of a version are compared. The actual separator characters are not significant: `1.a.1 == 1-a+1 == 1.a-1 == 1a1`</li>
 * </ul>
 * </li>
 * <li>The equivalent parts of 2 versions are compared using the following rules:
 * <ul>
 *     <li>If both parts are numeric, the highest numeric value is <b>higher</b>: `1.1 {@literal <} 1.2`</li>
 *     <li>If one part is numeric, it is considered <b>higher</b> than the non-numeric part: `1.a {@literal <} 1.1`</li>
 *     <li>If both are not numeric, the parts are compared alphabetically, case-sensitive: `1.A {@literal <} 1.B  {@literal <} 1.a {@literal <} 1.b`</li>
 *     <li>An version with an extra numeric part is considered <b>higher</b> than a version without: `1.1 {@literal <} 1.1.0`</li>
 *     <li>An version with an extra non-numeric part is considered <b>lower</b> than a version without: `1.1.a {@literal <} 1.1`</li>
 * </ul>
 * </li>
 * <li>Certain string values have special meaning for the purposes of ordering:
 * <ul>
 *     <li>The string "dev" is consider <b>lower</b> than any other string part: 1.0-dev {@literal <} 1.0-alpha {@literal <} 1.0-rc.</li>
 *     <li>The strings "rc", "release" and "final" are considered <b>higher</b> than any other string part (sorted in that order): 1.0-zeta {@literal <} 1.0-rc {@literal <} 1.0-release {@literal <} 1.0-final {@literal <} 1.0.</li>
 *     <li>The string "SNAPSHOT" <b>has no special meaning</b>, and is sorted alphabetically like any other string part: 1.0-alpha {@literal <} 1.0-SNAPSHOT {@literal <} 1.0-zeta {@literal <} 1.0-rc {@literal <} 1.0.</li>
 *     <li>Numeric snapshot versions <b>have no special meaning</b>, and are sorted like any other numeric part: 1.0 {@literal <} 1.0-20150201.121010-123 {@literal <} 1.1.</li>
 * </ul>
 * </li>
 * </ul>
 *
 * @since 4.4
 */
@UsedByScanPlugin
public interface VersionConstraint extends Describable {
    /**
     * The branch to select versions from. When not {@code null} selects only those versions that were built from the specified branch.
     *
     * @since 4.6
     */
    @Nullable
    String getBranch();

    /**
     * The required version of a module (which may be an exact version or a version range).
     *
     * The required version of a module can typically be upgraded during dependency resolution, but not downgraded.
     *
     * @return the required version, or empty string if no required version specified. Never null.
     */
    String getRequiredVersion();

    /**
     * The preferred version of a module (which may be an exact version or a version range).
     *
     * The preferred version of a module provides a hint when resolving the version,
     * but will not be honored in the presence of conflicting constraints.
     *
     * @return the preferred version, or empty string if no preferred version specified. Never null.
     */
    String getPreferredVersion();

    /**
     * The strictly required version of a module (which may be an exact version or a version range).
     *
     * The required version of a module is strictly enforced and cannot be upgraded or downgraded during dependency resolution.
     *
     * @return the strict version, or empty string if no required version specified. Never null.
     */
    String getStrictVersion();

    /**
     * Returns the list of versions that this module rejects  (which may be exact versions, or ranges, anything that fits into a version string).
     *
     * @return the list of rejected versions
     */
    List<String> getRejectedVersions();

}
