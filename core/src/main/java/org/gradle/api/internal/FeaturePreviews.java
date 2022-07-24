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

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.EnumSet;
import java.util.Set;

@ServiceScope(Scopes.BuildSession.class)
public class FeaturePreviews {

    /**
     * Feature previews that can be turned on.
     * A feature that is no longer relevant will have the {@code active} flag set to {@code false}.
     */
    public enum Feature {
        GROOVY_COMPILATION_AVOIDANCE(true),
        ONE_LOCKFILE_PER_PROJECT(false),
        VERSION_ORDERING_V2(false),
        VERSION_CATALOGS(false),
        TYPESAFE_PROJECT_ACCESSORS(true),
        STABLE_CONFIGURATION_CACHE(true);

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

        Feature(boolean active) {
            this.active = active;
        }

        /**
         * Returns whether the feature is still relevant.
         */
        public boolean isActive() {
            return active;
        }
    }

    private final EnumSet<Feature> enabledFeatures = EnumSet.noneOf(Feature.class);

    public void enableFeature(Feature feature) {
        if (feature.isActive()) {
            enabledFeatures.add(feature);
        }
    }

    public boolean isFeatureEnabled(Feature feature) {
        return enabledFeatures.contains(feature);
    }

    /**
     * Returns the set of active {@linkplain Feature features}.
     */
    public Set<Feature> getActiveFeatures() {
        return Feature.activeFeatures();
    }
}
