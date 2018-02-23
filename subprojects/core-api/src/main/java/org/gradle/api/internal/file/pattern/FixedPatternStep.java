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

package org.gradle.api.internal.file.pattern;

/**
 * A pattern step for a fixed pattern segment that does not contain any wildcards.
 */
public class FixedPatternStep implements PatternStep {
    private final String value;
    private final boolean caseSensitive;

    public FixedPatternStep(String value, boolean caseSensitive) {
        this.value = value;
        this.caseSensitive = caseSensitive;
    }

    @Override
    public String toString() {
        return "{match: " + value + "}";
    }

    public boolean matches(String candidate) {
        return caseSensitive ? candidate.equals(value) : candidate.equalsIgnoreCase(value);
    }
}
