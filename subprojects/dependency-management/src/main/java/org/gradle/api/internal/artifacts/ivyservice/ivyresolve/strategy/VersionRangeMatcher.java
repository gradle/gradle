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

import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;

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
public class VersionRangeMatcher implements VersionMatcher {
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

    private static final Pattern FINITE_RANGE = Pattern.compile(FINITE_PATTERN);

    private static final Pattern LOWER_INFINITE_RANGE = Pattern.compile(LOWER_INFINITE_PATTERN);

    private static final Pattern UPPER_INFINITE_RANGE = Pattern.compile(UPPER_INFINITE_PATTERN);

    private static final Pattern ALL_RANGE = Pattern.compile(FINITE_PATTERN + "|"
            + LOWER_INFINITE_PATTERN + "|" + UPPER_INFINITE_PATTERN);

    private final Comparator<String> staticVersionComparator;

    public VersionRangeMatcher(VersionMatcher staticVersionComparator) {
        this.staticVersionComparator = staticVersionComparator;
    }

    public boolean canHandle(String selector) {
        return ALL_RANGE.matcher(selector).matches();
    }

    public boolean isDynamic(String selector) {
        return true;
    }

    public boolean needModuleMetadata(String selector) {
        return false;
    }

    public boolean matchesUniqueVersion(String selector) {
        return false;
    }

    public boolean accept(String selector, String candidate) {
        Matcher matcher;
        matcher = FINITE_RANGE.matcher(selector);
        if (matcher.matches()) {
            String lower = matcher.group(1);
            String upper = matcher.group(2);
            return isHigher(candidate, lower, selector.startsWith(OPEN_INC))
                    && isLower(candidate, upper, selector.endsWith(CLOSE_INC));
        }
        matcher = LOWER_INFINITE_RANGE.matcher(selector);
        if (matcher.matches()) {
            String upper = matcher.group(1);
            return isLower(candidate, upper, selector.endsWith(CLOSE_INC));
        }
        matcher = UPPER_INFINITE_RANGE.matcher(selector);
        if (matcher.matches()) {
            String lower = matcher.group(1);
            return isHigher(candidate, lower, selector.startsWith(OPEN_INC));
        }
        throw new IllegalArgumentException("Not a version range selector: " + selector);
    }

    public boolean accept(String selector, ModuleComponentResolveMetaData candidate) {
        return accept(selector, candidate.getId().getVersion());
    }

    // doesn't seem to be quite in sync with accept() (e.g. open/close is not distinguished here)
    public int compare(String selector, String candidate) {
        Matcher m;
        m = UPPER_INFINITE_RANGE.matcher(selector);
        if (m.matches()) {
            // no upper limit, the selector can always be considered greater
            return 1;
        }
        String upper;
        m = FINITE_RANGE.matcher(selector);
        if (m.matches()) {
            upper = m.group(2);
        } else {
            m = LOWER_INFINITE_RANGE.matcher(selector);
            if (m.matches()) {
                upper = m.group(1);
            } else {
                throw new IllegalArgumentException("Not a version range selector: " + selector);
            }
        }
        int c = staticVersionComparator.compare(upper, candidate);
        // If the comparison considers them equal, we must return -1, because we can't consider the
        // dynamic version selector to be greater. Otherwise we can safely return the result of the static
        // comparison.
        return c == 0 ? -1 : c;
    }

    /**
     * Tells if version1 is lower than version2.
     */
    private boolean isLower(String version1, String version2, boolean inclusive) {
        int result = staticVersionComparator.compare(version1, version2);
        return result <= (inclusive ? 0 : -1);
    }

    /**
     * Tells if version1 is higher than version2.
     */
    private boolean isHigher(String version1, String version2, boolean inclusive) {
        int result = staticVersionComparator.compare(version1, version2);
        return result >= (inclusive ? 0 : 1);
    }
}
