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

public interface PathMatcher {
    /**
     * Returns the minimum number of segments a path must have to satisfy this matcher.
     */
    int getMinSegments();

    /**
     * Returns the maximum number of segments a path must have to satisfy this matcher.
     */
    int getMaxSegments();

    /**
     * Returns true if the path starting at the given offset satisfies this pattern.
     */
    boolean matches(String[] segments, int startIndex);

    /**
     * Returns true if the path starting at the given offset could be satisfy this pattern if it contained additional segments at the end.
     */
    boolean isPrefix(String[] segments, int startIndex);
}
