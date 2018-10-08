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
package org.gradle.internal.component.model;

import org.gradle.api.artifacts.ModuleIdentifier;

import javax.annotation.Nullable;

/**
 * Represents the complete model of an exclude rule supported by Gradle.
 * Several attributes of this model are not able to be configured in the DSL via {@link org.gradle.api.artifacts.ExcludeRule},
 * and are only present to support the rich exclude syntax supported in Ivy.xml files.
 */
public interface ExcludeMetadata {
    /**
     * The coordinates of the module to be excluded.
     * A '*' value for group or name indicates a wildcard match.
     */
    ModuleIdentifier getModuleId();

    /**
     * The attributes of the artifact to be excluded. A '*' value for any attribute indicates a wildcard match.
     * NOTE: only supported for exclude rules sourced from an Ivy module descriptor (ivy.xml).
     *
     * @return The IvyArtifactName to exclude, or `null` if no artifacts are excluded.
     */
    @Nullable
    IvyArtifactName getArtifact();

    /**
     * The name of the Ivy pattern matcher to use for this exclude.
     * NOTE: only supported for exclude rules sourced from an Ivy module descriptor (ivy.xml).
     *
     * @return The name of an Ivy pattern matcher, or `null` if the default Gradle matching should be used.
     */
    @Nullable
    String getMatcher();

}
