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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenVersionSelectorScheme implements VersionSelectorScheme {

    private static final String FIXED_PREFIX = "([\\d\\.]*)";
    private static final String DYN_VERSION_NUMBER = "(\\d+)";
    public static final String PLUS_OPER = "[\\.]?\\+";
    private static final String PLUS_NOTATION_PATTERN = FIXED_PREFIX + DYN_VERSION_NUMBER + PLUS_OPER;
    private static final String PLUS = "+";
    public static final String LATEST = "LATEST";
    public static final String RELEASE = "RELEASE";
    private static final String LATEST_INTEGRATION = "latest.integration";
    private static final String LATEST_RELEASE = "latest.release";

    public final Pattern plusNotationPattern = Pattern.compile(PLUS_NOTATION_PATTERN);

    private final VersionSelectorScheme defaultVersionSelectorScheme;

    public MavenVersionSelectorScheme(VersionSelectorScheme defaultVersionSelectorScheme) {
        this.defaultVersionSelectorScheme = defaultVersionSelectorScheme;
    }

    public VersionSelector parseSelector(String selectorString) {
        return defaultVersionSelectorScheme.parseSelector(fromMavenSyntax(selectorString));
    }

    public String renderSelector(VersionSelector selector) {
        return toMavenSyntax(defaultVersionSelectorScheme.renderSelector(selector));
    }

    // TODO:DAZ VersionSelector should be more descriptive, so it can be directly translated
    private String toMavenSyntax(String version) {
        Matcher plusNotationMatcher = plusNotationPattern.matcher(version);
        if (version.equals(PLUS) || version.equals(LATEST_INTEGRATION)) {
            return LATEST;
        }
        if (version.equals(LATEST_RELEASE)) {
            return RELEASE;
        }
        if (plusNotationMatcher.matches()) {
            String prefix = plusNotationMatcher.group(1);
            int dynVersionPart = Integer.parseInt(plusNotationMatcher.group(2));
            if (prefix != null) {
                return String.format("[%s%s,%s%s)", prefix, dynVersionPart, prefix, dynVersionPart + 1);
            } else {
                return String.format("[%s,%s)", dynVersionPart, dynVersionPart + 1);
            }
        }
        return version;
    }

    private String fromMavenSyntax(String version) {
        if (version.equals(RELEASE)) {
            return LATEST_RELEASE;
        } else if (version.equals(LATEST)) {
            return LATEST_INTEGRATION;
        } else {
            return version;
        }
    }
}
