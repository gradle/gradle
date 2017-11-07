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

    public DefaultVersionSelectorScheme(VersionComparator versionComparator) {
        this.versionComparator = versionComparator;
    }

    public VersionSelector parseSelector(String selectorString) {
        if (VersionRangeSelector.ALL_RANGE.matcher(selectorString).matches()) {
            return new VersionRangeSelector(selectorString, versionComparator.asVersionComparator());
        }

        if (selectorString.endsWith("+")) {
            return new SubVersionSelector(selectorString);
        }

        if (selectorString.startsWith("latest.")) {
            return new LatestVersionSelector(selectorString);
        }

        return new ExactVersionSelector(selectorString);
    }

    public String renderSelector(VersionSelector selector) {
        return ((AbstractVersionSelector) selector).getSelector();
    }

    @Override
    public VersionSelector complementForRejection(VersionSelector selector) {
        if (selector instanceof ExactVersionSelector) {
            return new VersionRangeSelector("]" + ((ExactVersionSelector) selector).getSelector() + ",)", versionComparator.asVersionComparator());
        }
        if (selector instanceof VersionRangeSelector) {
            VersionRangeSelector vrs = (VersionRangeSelector) selector;
            if (vrs.getUpperBound() != null) {
                String lowerBoundInclusion = vrs.isUpperInclusive() ? "]" : "[";
                return new VersionRangeSelector(lowerBoundInclusion + vrs.getUpperBound() + ",)", versionComparator.asVersionComparator());
            }
        }
        throw new IllegalArgumentException("Version '" + renderSelector(selector) + "' cannot be converted to a strict version constraint.");
    }
}
