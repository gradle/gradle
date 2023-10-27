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

package org.gradle.api.publish.internal.mapping;

import org.gradle.api.artifacts.ModuleVersionIdentifier;

import javax.annotation.Nullable;

/**
 * Represents the coordinates that a declared dependency should be published to.
 * Similar to {@link ModuleVersionIdentifier}, but allows a null version.
 */
public interface ResolvedCoordinates {

    /**
     * The group of the dependency to publish.
     */
    String getGroup();

    /**
     * The name of the dependency to publish.
     */
    String getName();

    /**
     * The version of the dependency to publish.
     */
    @Nullable
    String getVersion();

    static ResolvedCoordinates create(String group, String name, @Nullable String version) {
        return new ResolvedCoordinates() {
            @Override
            public String getGroup() {
                return group;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getVersion() {
                return version;
            }
        };
    }

    // Returns a separate implementation than `create` to avoid deconstructing the identifier.
    static ResolvedCoordinates create(ModuleVersionIdentifier identifier) {
        return new ResolvedCoordinates() {
            @Override
            public String getGroup() {
                return identifier.getGroup();
            }

            @Override
            public String getName() {
                return identifier.getName();
            }

            @Override
            public String getVersion() {
                return identifier.getVersion();
            }
        };
    }
}
