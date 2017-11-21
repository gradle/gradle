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

package org.gradle.internal.component.model;

import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.Set;

/**
 * Represents the complete model of an exclude rule supported by Gradle.
 * Several attributes of this model are not able to be configured in the DSL via {@link org.gradle.api.artifacts.ExcludeRule},
 * and are only present to support the rich exclude syntax supported in Ivy.xml files.
 */
public interface Exclude {
    /**
     * The coordinates of the module to be excluded.
     * A '*' value for group or name indicates a wildcard match.
     */
    ModuleIdentifier getModuleId();

    /**
     * The attributes of the artifact to be excluded.
     * NOTE: only supported for exclude rules sourced from an Ivy module descriptor (ivy.xml).
     */
    IvyArtifactName getArtifact();

    /**
     * The configurations that should be excluded.
     * NOTE: only supported for exclude rules sourced from an Ivy module descriptor (ivy.xml).
     */
    Set<String> getConfigurations();

    /**
     * The name of the Ivy pattern matcher to use for this exclude.
     * NOTE: only supported for exclude rules sourced from an Ivy module descriptor (ivy.xml).
     */
    String getMatcher();

}
