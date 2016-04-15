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

public class MavenVersionSelectorScheme implements VersionSelectorScheme {

    public static final String LATEST = "LATEST";
    public static final String RELEASE = "RELEASE";
    private static final String LATEST_INTEGRATION = "latest.integration";
    private static final String LATEST_RELEASE = "latest.release";

    private final VersionSelectorScheme defaultVersionSelectorScheme;

    public MavenVersionSelectorScheme(VersionSelectorScheme defaultVersionSelectorScheme) {
        this.defaultVersionSelectorScheme = defaultVersionSelectorScheme;
    }

    public VersionSelector parseSelector(String selectorString) {
        if (selectorString.equals(RELEASE)) {
            return new LatestVersionSelector(LATEST_RELEASE);
        } else if (selectorString.equals(LATEST)) {
            return new LatestVersionSelector(LATEST_INTEGRATION);
        } else {
            return defaultVersionSelectorScheme.parseSelector(selectorString);
        }
    }

    public String renderSelector(VersionSelector selector) {
        return toMavenSyntax(defaultVersionSelectorScheme.renderSelector(selector));
    }

    // TODO: VersionSelector should be more descriptive, so it can be directly translated
    private String toMavenSyntax(String version) {
        if (version.equals(LATEST_INTEGRATION)) {
            return LATEST;
        }
        if (version.equals(LATEST_RELEASE)) {
            return RELEASE;
        }
        if (version.startsWith("]")) {
            version = '(' + version.substring(1);
        }
        if (version.endsWith("[")) {
            version = version.substring(0, version.length() - 1) + ')';
        }
        return version;
    }
}
