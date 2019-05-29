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

    /**
     * This constructor is here to maintain backwards compatibility with the nebula plugins
     * and should be removed as soon as possible.
     */
    @Deprecated
    public DefaultVersionSelectorScheme(VersionComparator versionComparator) {
        this(versionComparator, new VersionParser());
    }

    public DefaultVersionSelectorScheme(VersionComparator versionComparator, VersionParser versionParser) {
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
    }

    @Override
    public VersionSelector parseSelector(String selectorString) {
        if (VersionRangeSelector.ALL_RANGE.matcher(selectorString).matches()) {
            return new VersionRangeSelector(selectorString, versionComparator.asVersionComparator(), versionParser);
        }

        if (selectorString.endsWith("+")) {
            return new SubVersionSelector(selectorString);
        }

        if (selectorString.startsWith("latest.")) {
            return new LatestVersionSelector(selectorString);
        }

        return new ExactVersionSelector(selectorString);
    }

    @Override
    public String renderSelector(VersionSelector selector) {
        return selector.getSelector();
    }

    @Override
    public VersionSelector complementForRejection(VersionSelector selector) {
        // TODO We can probably now support more versions with `strictly` but we'll need more test coverage
        if ((selector instanceof ExactVersionSelector)
            || (selector instanceof VersionRangeSelector && ((VersionRangeSelector) selector).getUpperBound() != null)) {
            return new InverseVersionSelector(selector);
        }
        throw new IllegalArgumentException("Version '" + renderSelector(selector) + "' cannot be converted to a strict version constraint.");
    }
}
