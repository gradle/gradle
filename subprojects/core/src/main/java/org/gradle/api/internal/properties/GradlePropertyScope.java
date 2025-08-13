/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.properties;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.project.ProjectIdentity;
import org.jspecify.annotations.Nullable;

/**
 * Scope of the property. Properties can be {@link Build build-scoped} or {@link Project project-scoped}.
 *
 * Can be used as map keys.
 */
/* sealed */ public interface GradlePropertyScope {

    @Override
    boolean equals(@Nullable Object o);

    @Override
    int hashCode();

    @Override
    String toString();

    interface Build extends GradlePropertyScope {
        BuildIdentifier getBuildIdentifier();
    }

    interface Project extends GradlePropertyScope {
        ProjectIdentity getProjectIdentity();
    }
}
