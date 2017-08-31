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

package org.gradle.process.internal.health.memory;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.util.Locale;

public class MemoryAmount {
    private static final long KILO_FACTOR = 1024;
    private static final long MEGA_FACTOR = KILO_FACTOR * 1024;
    private static final long GIGA_FACTOR = MEGA_FACTOR * 1024;
    private static final long TERA_FACTOR = GIGA_FACTOR * 1024;

    public static MemoryAmount of(long bytes) {
        return new MemoryAmount(bytes, String.valueOf(bytes));
    }

    public static MemoryAmount ofKiloBytes(long kiloBytes) {
        long bytes = kiloBytes * KILO_FACTOR;
        return new MemoryAmount(bytes, bytes + "k");
    }

    public static MemoryAmount ofMegaBytes(long megaBytes) {
        long bytes = megaBytes * MEGA_FACTOR;
        return new MemoryAmount(bytes, bytes + "m");
    }

    public static MemoryAmount ofGigaBytes(long gigaBytes) {
        long bytes = gigaBytes * GIGA_FACTOR;
        return new MemoryAmount(bytes, bytes + "g");
    }

    public static MemoryAmount ofTeraBytes(long teraBytes) {
        long bytes = teraBytes * TERA_FACTOR;
        return new MemoryAmount(bytes, bytes + "t");
    }

    public static MemoryAmount of(String notation) {
        return new MemoryAmount(parseNotation(notation), notation);
    }

    /**
     * Parse memory amount notation.
     *
     * @return The parsed memory amount in bytes, {@literal -1} if the notation is {@literal null} or empty.
     * @throws IllegalArgumentException if the notation is invalid
     */
    public static long parseNotation(@Nullable String notation) {
        if (notation == null) {
            return -1;
        }
        String normalized = notation.toLowerCase(Locale.US).trim();
        if (normalized.isEmpty()) {
            return -1;
        }
        try {
            if (normalized.endsWith("k")) {
                return parseWithFactor(normalized, KILO_FACTOR);
            }
            if (normalized.endsWith("m")) {
                return parseWithFactor(normalized, MEGA_FACTOR);
            }
            if (normalized.endsWith("g")) {
                return parseWithFactor(normalized, GIGA_FACTOR);
            }
            if (normalized.endsWith("t")) {
                return parseWithFactor(normalized, TERA_FACTOR);
            }
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Cannot parse memory amount notation: " + notation, ex);
        }
    }

    private static long parseWithFactor(String notation, long factor) {
        return Long.parseLong(notation.substring(0, notation.length() - 1)) * factor;
    }

    private final long bytes;
    private final String notation;

    private MemoryAmount(long bytes, String notation) {
        Preconditions.checkArgument(bytes > 0, "bytes must be positive");
        this.bytes = bytes;
        this.notation = notation;
    }

    public long getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return notation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MemoryAmount that = (MemoryAmount) o;
        return bytes == that.bytes;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(bytes);
    }
}
