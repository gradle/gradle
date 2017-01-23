/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.attributes;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.gradle.api.attributes.Usage;

/**
 * An utility class for managing {@link Usage usages}.
 */
public abstract class Usages {
    private final static Interner<UsageImpl> USAGES = Interners.newStrongInterner();

    /**
     * Creates a simple named usage.
     * @param usage the usage name
     * @return a usage with the provided name
     */
    public static Usage usage(final String usage) {
        return USAGES.intern(new UsageImpl(usage));
    }

    private static class UsageImpl implements Usage {
        private final String usage;

        public UsageImpl(String usage) {
            this.usage = usage;
        }

        @Override
        public String getName() {
            return usage;
        }

        @Override
        public String toString() {
            return usage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            UsageImpl usage1 = (UsageImpl) o;

            return usage.equals(usage1.usage);

        }

        @Override
        public int hashCode() {
            return usage.hashCode();
        }
    }
}
