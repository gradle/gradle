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
package org.gradle.api.internal;

import org.gradle.internal.buildoption.FeatureFlag;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class FeaturePreviews {

    /**
     * Feature previews that can be turned on.
     * A feature that is no longer relevant will have the {@code active} flag set to {@code false}.
     */
    public enum Feature implements FeatureFlag {
        GROOVY_COMPILATION_AVOIDANCE(true, null),
        TYPESAFE_PROJECT_ACCESSORS(true, null),
        STABLE_CONFIGURATION_CACHE(true, "org.gradle.configuration-cache.stable");

        public static Feature withName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException e) {
                // Re-wording to exception message to get rid of the fqcn it contains
                throw new IllegalArgumentException("There is no feature named " + name);
            }
        }

        /**
         * Returns the set of active {@linkplain Feature features}.
         */
        private static EnumSet<Feature> activeFeatures() {
            EnumSet<Feature> activeFeatures = EnumSet.noneOf(Feature.class);
            for (Feature feature : Feature.values()) {
                if (feature.isActive()) {
                    activeFeatures.add(feature);
                }
            }
            return activeFeatures;
        }

        private final boolean active;
        private final String systemPropertyName;

        Feature(boolean active, @Nullable String systemPropertyName) {
            this.active = active;
            this.systemPropertyName = systemPropertyName;
        }

        /**
         * Returns whether the feature is still relevant.
         */
        public boolean isActive() {
            return active;
        }

        @Nullable
        @Override
        public String getSystemPropertyName() {
            return systemPropertyName;
        }
    }
}
