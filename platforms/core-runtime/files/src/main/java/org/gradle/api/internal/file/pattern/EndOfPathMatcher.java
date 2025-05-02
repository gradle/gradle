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

public class EndOfPathMatcher implements PathMatcher {
    @Override
    public String toString() {
        return "{end-of-path}";
    }

    @Override
    public int getMaxSegments() {
        return 0;
    }

    @Override
    public int getMinSegments() {
        return 0;
    }

    @Override
    public boolean matches(String[] segments, int startIndex) {
        return startIndex == segments.length;
    }

    @Override
    public boolean isPrefix(String[] segments, int startIndex) {
        return false;
    }
}
