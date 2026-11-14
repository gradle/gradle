/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic;

import org.jspecify.annotations.NullMarked;

/**
 * Remembers the most recent characters added to it, up to a fixed capacity, to check what a stream of characters
 * currently ends with.
 *
 * <p>
 * This is a ring buffer, so adding a character is O(1) regardless of the capacity.
 * </p>
 */
@NullMarked
final class RecentChars {
    private final char[] buffer;
    /**
     * Where the next character goes in {@link #buffer}.
     */
    private int end;
    /**
     * How many characters of {@link #buffer} are filled, which is less than its capacity only at the start.
     */
    private int length;

    RecentChars(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, but was " + capacity + ".");
        }
        this.buffer = new char[capacity];
    }

    void add(char c) {
        buffer[end] = c;
        end++;
        if (end == buffer.length) {
            end = 0;
        }
        if (length < buffer.length) {
            length++;
        }
    }

    /**
     * Checks if the most recent characters are the given text, ignoring case.
     *
     * @param text the text to check for, which must be lowercase and no longer than the capacity
     */
    boolean endsWithIgnoreCase(String text) {
        if (text.length() > buffer.length) {
            throw new IllegalArgumentException("Text '" + text + "' is longer than the capacity " + buffer.length + ".");
        }
        if (length < text.length()) {
            return false;
        }
        int index = end - text.length();
        if (index < 0) {
            index += buffer.length;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.toLowerCase(buffer[index]) != text.charAt(i)) {
                return false;
            }
            index++;
            if (index == buffer.length) {
                index = 0;
            }
        }
        return true;
    }
}
