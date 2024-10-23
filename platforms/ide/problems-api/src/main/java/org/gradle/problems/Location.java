/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.problems;

import org.gradle.internal.DisplayName;

/**
 * A source file location.
 */
public class Location {
    private final int lineNumber;
    private final DisplayName sourceLongDisplayName;
    private final DisplayName sourceShortDisplayName;

    public Location(DisplayName sourceLongDisplayName, DisplayName sourceShortDisplayName, int lineNumber) {
        this.sourceLongDisplayName = sourceLongDisplayName;
        this.sourceShortDisplayName = sourceShortDisplayName;
        this.lineNumber = lineNumber;
    }

    /**
     * Returns a long display name for the source file containing this location. The long description should use absolute paths and assume no particular context.
     */
    public DisplayName getSourceLongDisplayName() {
        return sourceLongDisplayName;
    }

    /**
     * Returns a short display name for the source file containing this location. The short description may use relative paths.
     */
    public DisplayName getSourceShortDisplayName() {
        return sourceShortDisplayName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getFormatted() {
        return sourceLongDisplayName.getCapitalizedDisplayName() + ": line " + lineNumber;
    }

    @Override
    public String toString() {
        return getFormatted();
    }
}
