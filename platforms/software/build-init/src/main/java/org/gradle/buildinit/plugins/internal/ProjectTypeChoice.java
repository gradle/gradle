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

package org.gradle.buildinit.plugins.internal;

import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.specs.BuildInitSpec;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * An option of the "Select type of build to generate" question: a built-in component type,
 * or a project type that a plugin contributed.
 */
public final class ProjectTypeChoice {
    private final @Nullable ComponentType componentType;
    private final @Nullable BuildInitSpec contributedSpec;

    private ProjectTypeChoice(@Nullable ComponentType componentType, @Nullable BuildInitSpec contributedSpec) {
        this.componentType = componentType;
        this.contributedSpec = contributedSpec;
    }

    public static ProjectTypeChoice of(ComponentType componentType) {
        return new ProjectTypeChoice(componentType, null);
    }

    public static ProjectTypeChoice of(BuildInitSpec contributedSpec) {
        return new ProjectTypeChoice(null, contributedSpec);
    }

    public String getDisplayName() {
        if (contributedSpec != null) {
            return contributedSpec.getDisplayName() + " (contributed by a plugin)";
        }
        return Objects.requireNonNull(componentType).getDisplayName();
    }

    /**
     * Returns the built-in component type, or {@code null} if a plugin contributed this choice.
     */
    public @Nullable ComponentType getComponentType() {
        return componentType;
    }

    /**
     * Returns the contributed spec, or {@code null} if this choice is a built-in component type.
     */
    public @Nullable BuildInitSpec getContributedSpec() {
        return contributedSpec;
    }
}
