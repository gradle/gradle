/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

public class DefaultVersionSelectorScheme implements VersionSelectorScheme {
    private final VersionComparator versionComparator;
    private final VersionParser versionParser;

    public DefaultVersionSelectorScheme(VersionComparator versionComparator, VersionParser versionParser) {
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
    }

    @Override
    public VersionSelector parseSelector(String selectorString) {
        if (VersionRangeSelector.ALL_RANGE.matcher(selectorString).matches()) {
            return maybeCreateRangeSelector(selectorString);
        }

        if (isSubVersion(selectorString)) {
            return new SubVersionSelector(selectorString);
        }

        if (isLatestVersion(selectorString)) {
            return new LatestVersionSelector(selectorString);
        }

        return new ExactVersionSelector(selectorString);
    }

    private VersionSelector maybeCreateRangeSelector(String selectorString) {
        VersionRangeSelector rangeSelector = new VersionRangeSelector(selectorString, versionComparator.asVersionComparator(), versionParser);
        if (isSingleVersionRange(rangeSelector)) {
            // it's a single version range, like [1.0] or [1.0, 1.0]
            return new ExactVersionSelector(rangeSelector.getUpperBound());
        }
        return rangeSelector;
    }

    private static boolean isSingleVersionRange(VersionRangeSelector rangeSelector) {
        String lowerBound = rangeSelector.getLowerBound();
        return lowerBound != null &&
            lowerBound.equals(rangeSelector.getUpperBound()) &&
            rangeSelector.isLowerInclusive() && rangeSelector.isUpperInclusive();
    }

    @Override
    public String renderSelector(VersionSelector selector) {
        return selector.getSelector();
    }

    @Override
    public VersionSelector complementForRejection(VersionSelector selector) {
        return new InverseVersionSelector(selector);
    }

    public static boolean isSubVersion(String selectorString) {
        return selectorString.endsWith("+");
    }

    public static boolean isLatestVersion(String selectorString) {
        return selectorString.startsWith("latest.");
    }
}
