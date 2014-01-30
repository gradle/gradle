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
    public static PatternStep getStep(String source, boolean caseSensitive) {
        if (source.length() == 0) {
            return new FixedPatternStep(source, caseSensitive);
        }

        // Handle '**' and '*some-pattern' special cases
        char ch = source.charAt(0);
        if (ch == '*') {
            if (source.length() == 1) {
                return new AnyWildcardPatternStep();
            }
            if (source.length() == 2 && source.charAt(1) == '*') {
                return new GreedyPatternStep();
            }
            int pos = 1;
            while (pos < source.length() && source.charAt(pos) == '*') {
                pos++;
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
                return new RegExpPatternStep(source, caseSensitive);
            }
        }
        return new FixedPatternStep(source, caseSensitive);
    }
}
