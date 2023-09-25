/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.pattern;

public class PatternStepFactory {
    private static final AnyWildcardPatternStep ANY_WILDCARD_PATTERN_STEP = new AnyWildcardPatternStep();

    public static PatternStep getStep(String source, boolean caseSensitive) {
        if (source.length() == 0) {
            return new FixedPatternStep(source, caseSensitive);
        }

        // Here, we try to avoid using the reg exp backed pattern step, as it is expensive in terms of performance and heap usage.
        // There are several special cases we handle here:
        // 1. '*'
        // 2. '*' <literal>
        // 3. <literal> '*'
        // 4. <literal> '*' <literal>
        // 5. <literal>
        // Everything else uses a reg exp.

        // Not empty: may match any case above

        char ch = source.charAt(0);
        int endPrefixWildcard = 0;
        if (ch == '*') {
            endPrefixWildcard = 1;
            while (endPrefixWildcard < source.length() && source.charAt(endPrefixWildcard) == '*') {
                endPrefixWildcard++;
            }
        }

        if (endPrefixWildcard == source.length()) {
            // Only * characters: matches #1 above
            return ANY_WILDCARD_PATTERN_STEP;
        }

        // Zero or more * characters followed by at least one !*

        int endLiteral = endPrefixWildcard;
        for(; endLiteral < source.length(); endLiteral++) {
            ch = source.charAt(endLiteral);
            if (ch == '?') {
                // No matches - fall back to regexp
                return new RegExpPatternStep(source, caseSensitive);
            }
            if (ch == '*') {
                break;
            }
        }
        if (endLiteral == source.length()) {
            if (endPrefixWildcard == 0) {
                // No wildcards: matches #5 above
                return new FixedPatternStep(source, caseSensitive);
            }
            // One or more '*' followed by one or more non-wildcard: matches #2 above
            return new HasSuffixPatternStep(source.substring(endPrefixWildcard), caseSensitive);
        }

        // Zero or more * characters followed by literal followed by at least one *

        if (endPrefixWildcard > 0) {
            // No matches - fall back to regexp
            return new RegExpPatternStep(source, caseSensitive);
        }

        // literal followed by at least one *

        int endSuffixWildcard = endLiteral;
        for (; endSuffixWildcard < source.length(); endSuffixWildcard++) {
            ch = source.charAt(endSuffixWildcard);
            if (ch != '*') {
                break;
            }
        }

        if (endSuffixWildcard == source.length()) {
            // Literal followed by *: matches #3 above
            return new HasPrefixPatternStep(source.substring(0, endLiteral), caseSensitive);
        }

        for (int i = endSuffixWildcard; i < source.length(); i++) {
            ch = source.charAt(i);
            if (ch == '?' || ch == '*') {
                // No matches - fall back to regexp
                return new RegExpPatternStep(source, caseSensitive);
            }
        }

        // literal followed by * followed by literal: matches #4 above
        return new HasPrefixAndSuffixPatternStep(source.substring(0, endLiteral), source.substring(endSuffixWildcard), caseSensitive);
    }
}
