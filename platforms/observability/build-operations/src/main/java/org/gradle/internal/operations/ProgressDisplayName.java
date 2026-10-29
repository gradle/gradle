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

package org.gradle.internal.operations;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The display name to use for progress logging for an operation, with the owner to show.
 */
public final class ProgressDisplayName {
    private final @Nullable String owner;
    private final String text;

    private ProgressDisplayName(@Nullable String owner, String text) {
        this.owner = owner;
        this.text = text;
    }

    public static ProgressDisplayName of(String text) {
        return new ProgressDisplayName(null, text);
    }

    public static ProgressDisplayName of(@Nullable String owner, String text) {
        return new ProgressDisplayName(owner, text);
    }

    /**
     * The owner to show with the text, such as ":lib". Null when no owner is shown.
     */
    @Nullable
    public String getOwner() {
        return owner;
    }

    /**
     * Short, and describes the operation as it is running, eg "Running test A" rather than "Run test A".
     */
    public String getText() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProgressDisplayName that = (ProgressDisplayName) o;
        return Objects.equals(owner, that.owner) && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, text);
    }

    @Override
    public String toString() {
        return owner == null ? text : text + " (" + owner + ")";
    }
}
