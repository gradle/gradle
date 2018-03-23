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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.NestedBuildFactory;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * A registry of all the builds present in a build tree.
 */
public interface IncludedBuildRegistry {
    /**
     * Returns all children of the root build.
     */
    Collection<? extends IncludedBuildInternal> getIncludedBuilds();

    /**
     * Locates a build by {@link BuildIdentifier}, if present.
     */
    @Nullable
    IncludedBuildInternal getBuild(BuildIdentifier buildIdentifier);

    void validateExplicitIncludedBuilds(SettingsInternal settings);

    /**
     * Registers an included build.
     */
    IncludedBuildInternal addExplicitBuild(BuildDefinition buildDefinition, NestedBuildFactory nestedBuildFactory);

    /**
     * Registers a child build that is not an included.
     */
    IncludedBuildInternal addImplicitBuild(BuildDefinition buildDefinition, NestedBuildFactory nestedBuildFactory);
}
