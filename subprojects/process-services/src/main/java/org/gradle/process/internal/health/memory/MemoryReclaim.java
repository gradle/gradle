/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.NonNullApi;

/**
 * Result of checking if memory should be freed/reclaimed.
 */
@NonNullApi
public abstract class MemoryReclaim {
    public static MemoryReclaim some(String type, long currentFree, long requiredFree) {
        return new MemoryReclaim.Some(type, currentFree, requiredFree);
    }

    public static MemoryReclaim none() {
        return MemoryReclaim.None.INSTANCE;
    }

    /**
     * No reclaim should be performed.
     */
    @NonNullApi
    public static final class None extends MemoryReclaim {
        private static final None INSTANCE = new None();

        private None() {}

        @Override
        public MemoryReclaim merge(MemoryReclaim other) {
            // if other is Some, we want it; if other is None, we're equivalent, so we can also return it
            return other;
        }

        @Override
        public String toString() {
            return "MemoryReclaim.None";
        }
    }

    /**
     * Record of what memory type required a reclaim, and how much memory should be reclaimed.
     */
    @NonNullApi
    public static final class Some extends MemoryReclaim {
        private final String type;
        private final long currentFree;
        private final long requiredFree;

        private Some(String type, long currentFree, long requiredFree) {
            if (requiredFree <= currentFree) {
                throw new IllegalArgumentException(
                    "Required free memory (" + requiredFree + ") must be greater than current free memory (" + currentFree + ")"
                );
            }
            this.type = type;
            this.currentFree = currentFree;
            this.requiredFree = requiredFree;
        }

        /**
         * The type of memory that should be reclaimed.
         *
         * @return the type of memory
         */
        public String getType() {
            return type;
        }

        /**
         * The amount of memory (in bytes) that is currently free.
         *
         * @return the current free amount
         */
        public long getCurrentFree() {
            return currentFree;
        }

        /**
         * The amount of memory (in bytes) that was required to be free.
         *
         * @return the required free amount
         */
        public long getRequiredFree() {
            return requiredFree;
        }

        /**
         * The amount of memory (in bytes) that should be reclaimed.
         *
         * @return the amount of memory
         */
        public long getAmount() {
            return requiredFree - currentFree;
        }

        @Override
        public MemoryReclaim merge(MemoryReclaim other) {
            if (other instanceof None) {
                return this;
            }
            if (other instanceof Some) {
                Some otherSome = (Some) other;
                if (otherSome.getAmount() > getAmount()) {
                    return otherSome;
                }
            }
            throw new AssertionError("Unexpected memory reclaim type: " + other.getClass().getName());
        }

        @Override
        public String toString() {
            return "MemoryReclaim.Some{"
                + "type=" + type + ", "
                + "currentFree=" + currentFree + ", "
                + "requiredFree=" + requiredFree
                + '}';
        }
    }

    private MemoryReclaim() {
    }

    /**
     * Merge this reclaim with another reclaim. The reclaim with the highest amount will be returned.
     * If the amounts are equal, this reclaim will be returned. If the other reclaim is {@link None}, this reclaim will be returned.
     *
     * @param other the other reclaim
     * @return the merged reclaim
     */
    public abstract MemoryReclaim merge(MemoryReclaim other);
}
