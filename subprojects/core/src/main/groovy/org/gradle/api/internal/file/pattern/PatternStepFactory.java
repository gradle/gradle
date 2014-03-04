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
        // There are 3 special cases we handle here:
        // 1. '*'
        // 2. '*' <literal>
        // 3. <literal>
        // Everything else uses a reg exp.

        // Handle '**' and '*some-pattern' special cases
        char ch = source.charAt(0);
        if (ch == '*') {
            int pos = 1;
            while (pos < source.length() && source.charAt(pos) == '*') {
                pos++;
            }
            if (pos == source.length()) {
                return ANY_WILDCARD_PATTERN_STEP;
            }
            for (int i = pos; i < source.length(); i++) {
                ch = source.charAt(i);
                if (ch == '?' || ch == '*') {
                    // Too complicated - fall back to regexp
                    return new RegExpPatternStep(source, caseSensitive);
                }
            }
            return new WildcardPrefixPatternStep(source.substring(pos), caseSensitive);
        }

        for (int i = 0; i < source.length(); i++) {
            ch = source.charAt(i);
            if (ch == '?' || ch == '*') {
                // Too complicated - fall back to regexp
                return new RegExpPatternStep(source, caseSensitive);
            }
        }
        return new FixedPatternStep(source, caseSensitive);
    }
}
