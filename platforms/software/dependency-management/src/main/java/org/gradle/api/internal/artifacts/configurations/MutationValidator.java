/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

/**
 * Used to validate mutation of an object and its sub-parts.
 */
public interface MutationValidator {
    enum MutationType {
        /**
         * The mutation of the resolution strategy of the configuration, i.e. caching, resolution rules etc.
         */
        STRATEGY("resolution strategy"),

        /**
         * The mutation of anything that will affect the resolved dependency graph of this configuration.
         */
        DEPENDENCIES("dependencies"),

        /**
         * The mutation of the attributes (other than coordinates) of a dependency.
         * Theoretically these should be bundled under {@link MutationType#DEPENDENCIES}, but these mutations are not (yet)
         * prevented on resolved configurations.
         */
        DEPENDENCY_ATTRIBUTES("dependency attributes"),

        /**
         * The mutation of the artifacts of the configuration.
         */
        ARTIFACTS("artifacts"),

        /**
         * The mutation of the allowed usage of the configuration (can be consumed, resolved, deprecated for declaration against...)
         */
        USAGE("usage"),

        /**
         * The mutation of the role of the configuration (can be queries, resolved, ...)
         */
        ROLE("role"),

        /**
         * The mutation of the hierarchy of the configuration, i.e. which configurations this configuration extends from.
         */
        HIERARCHY("hierarchy");

        private final String displayName;

        MutationType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Check if mutation is allowed.
     *
     * @param type the type of mutation to validate.
     */
    void validateMutation(MutationType type);

    MutationValidator IGNORE = type -> {
    };
}
