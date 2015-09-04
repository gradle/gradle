/*
 * Copyright 2013 the original author or authors.
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

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches version ranges: [1.0,2.0] matches all versions greater or equal to 1.0 and lower or equal
 * to 2.0 [1.0,2.0[ matches all versions greater or equal to 1.0 and lower than 2.0 ]1.0,2.0]
 * matches all versions greater than 1.0 and lower or equal to 2.0 ]1.0,2.0[ matches all versions
 * greater than 1.0 and lower than 2.0 [1.0,) matches all versions greater or equal to 1.0 ]1.0,)
 * matches all versions greater than 1.0 (,2.0] matches all versions lower or equal to 2.0 (,2.0[
 * matches all versions lower than 2.0 This class uses a latest strategy to compare revisions. If
 * none is set, it uses the default one of the ivy instance set through setIvy(). If neither a
 * latest strategy nor a ivy instance is set, an IllegalStateException will be thrown when calling
 * accept(). Note that it can't work with latest time strategy, cause no time is known for the
 * limits of the range. Therefore only purely revision based LatestStrategy can be used.
 */
public class VersionRangeSelector extends AbstractVersionSelector {
    private static final String OPEN_INC = "[";

    private static final String OPEN_EXC = "]";
    private static final String OPEN_EXC_MAVEN = "(";

    private static final String CLOSE_INC = "]";

    private static final String CLOSE_EXC = "[";
    private static final String CLOSE_EXC_MAVEN = ")";

    private static final String LOWER_INFINITE = "(";

    private static final String UPPER_INFINITE = ")";

    private static final String SEPARATOR = ",";

    // following patterns are built upon constants above and should not be modified
    private static final String OPEN_INC_PATTERN = "\\" + OPEN_INC;

    private static final String OPEN_EXC_PATTERN = "\\" + OPEN_EXC + "\\" + OPEN_EXC_MAVEN;

    private static final String CLOSE_INC_PATTERN = "\\" + CLOSE_INC;

    private static final String CLOSE_EXC_PATTERN = "\\" + CLOSE_EXC + "\\" + CLOSE_EXC_MAVEN;

    private static final String LI_PATTERN = "\\" + LOWER_INFINITE;

    private static final String UI_PATTERN = "\\" + UPPER_INFINITE;

    private static final String SEP_PATTERN = "\\s*\\" + SEPARATOR + "\\s*";

    private static final String OPEN_PATTERN = "[" + OPEN_INC_PATTERN + OPEN_EXC_PATTERN + "]";

    private static final String CLOSE_PATTERN = "[" + CLOSE_INC_PATTERN + CLOSE_EXC_PATTERN + "]";

    private static final String ANY_NON_SPECIAL_PATTERN = "[^\\s" + SEPARATOR + OPEN_INC_PATTERN
            + OPEN_EXC_PATTERN + CLOSE_INC_PATTERN + CLOSE_EXC_PATTERN + LI_PATTERN + UI_PATTERN
            + "]";

    private static final String FINITE_PATTERN = OPEN_PATTERN + "\\s*(" + ANY_NON_SPECIAL_PATTERN
            + "+)" + SEP_PATTERN + "(" + ANY_NON_SPECIAL_PATTERN + "+)\\s*" + CLOSE_PATTERN;

    private static final String LOWER_INFINITE_PATTERN = LI_PATTERN + SEP_PATTERN + "("
            + ANY_NON_SPECIAL_PATTERN + "+)\\s*" + CLOSE_PATTERN;

    private static final String UPPER_INFINITE_PATTERN = OPEN_PATTERN + "\\s*("
            + ANY_NON_SPECIAL_PATTERN + "+)" + SEP_PATTERN + UI_PATTERN;

    private static final String SINGLE_VALUE_PATTERN = OPEN_INC_PATTERN + "\\s*(" + ANY_NON_SPECIAL_PATTERN + "+)" + CLOSE_INC_PATTERN;

    private static final Pattern FINITE_RANGE = Pattern.compile(FINITE_PATTERN);

    private static final Pattern LOWER_INFINITE_RANGE = Pattern.compile(LOWER_INFINITE_PATTERN);

    private static final Pattern UPPER_INFINITE_RANGE = Pattern.compile(UPPER_INFINITE_PATTERN);

    private static final Pattern SINGLE_VALUE_RANGE = Pattern.compile(SINGLE_VALUE_PATTERN);

    public static final Pattern ALL_RANGE = Pattern.compile(FINITE_PATTERN + "|"
            + LOWER_INFINITE_PATTERN + "|" + UPPER_INFINITE_PATTERN + "|" + SINGLE_VALUE_RANGE);

    private final String upperBound;
    private final boolean upperInclusive;
    private final String lowerBound;
    private final boolean lowerInclusive;
    private final Comparator<String> comparator;

    public VersionRangeSelector(String selector, Comparator<String> comparator) {
        super(selector);
        this.comparator = comparator;

        Matcher matcher;
        matcher = FINITE_RANGE.matcher(selector);
        if (matcher.matches()) {
            lowerBound = matcher.group(1);
            lowerInclusive = selector.startsWith(OPEN_INC);
            upperBound = matcher.group(2);
            upperInclusive = selector.endsWith(CLOSE_INC);
        } else {
            matcher = LOWER_INFINITE_RANGE.matcher(selector);
            if (matcher.matches()) {
                lowerBound = null;
                lowerInclusive = true;
                upperBound = matcher.group(1);
                upperInclusive = selector.endsWith(CLOSE_INC);
            } else {
                matcher = UPPER_INFINITE_RANGE.matcher(selector);
                if (matcher.matches()) {
                    lowerBound = matcher.group(1);
                    lowerInclusive = selector.startsWith(OPEN_INC);
                    upperBound = null;
                    upperInclusive = true;
                } else {
                    matcher = SINGLE_VALUE_RANGE.matcher(selector);
                    if (matcher.matches()) {
                        lowerBound = matcher.group(1);
                        lowerInclusive = true;
                        upperBound = lowerBound;
                        upperInclusive = true;
                    } else {
                        throw new IllegalArgumentException("Not a version range selector: " + selector);
                    }
                }
            }
        }
    }

    public boolean isDynamic() {
        return true;
    }

    public boolean requiresMetadata() {
        return false;
    }

    public boolean matchesUniqueVersion() {
        return false;
    }

    public boolean accept(String candidate) {
        if (lowerBound != null && !isHigher(candidate, lowerBound, lowerInclusive)) {
            return false;
        }
        if (upperBound != null && !isLower(candidate, upperBound, upperInclusive)) {
            return false;
        }
        return true;
    }

    /**
     * Tells if version1 is lower than version2.
     */
    private boolean isLower(String version1, String version2, boolean inclusive) {
        int result = comparator.compare(version1, version2);
        return result <= (inclusive ? 0 : -1);
    }

    /**
     * Tells if version1 is higher than version2.
     */
    private boolean isHigher(String version1, String version2, boolean inclusive) {
        int result = comparator.compare(version1, version2);
        return result >= (inclusive ? 0 : 1);
    }
}
