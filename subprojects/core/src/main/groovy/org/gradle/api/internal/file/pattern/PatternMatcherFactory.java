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

import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;

public class PatternMatcherFactory {
    public static Spec<RelativePath> getPatternMatcher(boolean partialMatchDirs, boolean caseSensitive, String pattern) {
        // trailing / or \ assumes **
        if (pattern.endsWith("/") || pattern.endsWith("\\")) {
            pattern = pattern + "**";
        }

        if (pattern.length() == 0) {
            return new DefaultPatternMatcher(partialMatchDirs, true);
        } else {
            String[] parts = pattern.split("\\\\|/");
            if (parts.length == 2) {
                if ("**".equals(parts[0])) {
                    if ("**".equals(parts[1])) {
                        // don't need second **
                        return new DefaultPatternMatcher(partialMatchDirs, caseSensitive, "**");
                    } else {
                        // common name only case
                        return new NameOnlyPatternMatcher(partialMatchDirs, caseSensitive, parts[1]);
                    }
                }
            }
            return new DefaultPatternMatcher(partialMatchDirs, caseSensitive, parts);
        }
    }
}
