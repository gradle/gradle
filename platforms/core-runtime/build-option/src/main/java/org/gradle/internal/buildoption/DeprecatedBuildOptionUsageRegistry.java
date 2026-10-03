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

package org.gradle.internal.buildoption;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Process-wide collector for {@link BuildOption} reads that took the deprecated property
 * branch. Populated by {@link AbstractBuildOption#getFromProperties}, drained later by code
 * that has access to {@code DeprecationLogger} (the {@code build-option} module intentionally
 * does not depend on logging).
 */
public final class DeprecatedBuildOptionUsageRegistry {

    private static final Set<DeprecatedUsage> RECORDED = new LinkedHashSet<>();

    private DeprecatedBuildOptionUsageRegistry() {
    }

    public static synchronized void record(String deprecatedProperty, String replacementProperty) {
        RECORDED.add(new DeprecatedUsage(deprecatedProperty, replacementProperty));
    }

    public static synchronized Set<DeprecatedUsage> drain() {
        Set<DeprecatedUsage> snapshot = new LinkedHashSet<>(RECORDED);
        RECORDED.clear();
        return snapshot;
    }

    public static final class DeprecatedUsage {
        private final String deprecatedProperty;
        private final String replacementProperty;

        public DeprecatedUsage(String deprecatedProperty, String replacementProperty) {
            this.deprecatedProperty = deprecatedProperty;
            this.replacementProperty = replacementProperty;
        }

        public String getDeprecatedProperty() {
            return deprecatedProperty;
        }

        public String getReplacementProperty() {
            return replacementProperty;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DeprecatedUsage)) {
                return false;
            }
            DeprecatedUsage other = (DeprecatedUsage) o;
            return deprecatedProperty.equals(other.deprecatedProperty)
                && replacementProperty.equals(other.replacementProperty);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deprecatedProperty, replacementProperty);
        }
    }
}
