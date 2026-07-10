/*
 * Copyright 2016 the original author or authors.
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

/**
 * A pattern step for a pattern segment a the common case with a '*' suffix on the pattern. e.g. '._*'
 */
public class HasPrefixPatternStep implements PatternStep {
    private final String prefix;
    private final boolean caseSensitive;
    private final int prefixLength;

    public HasPrefixPatternStep(String prefix, boolean caseSensitive) {
        this.prefix = prefix;
        prefixLength = prefix.length();
        this.caseSensitive = caseSensitive;
    }

    @Override
    public String toString() {
        return "{prefix: " + prefix + "}";
    }

    @Override
    public boolean matches(String candidate) {
        return candidate.regionMatches(!caseSensitive, 0, prefix, 0, prefixLength);
    }
}
