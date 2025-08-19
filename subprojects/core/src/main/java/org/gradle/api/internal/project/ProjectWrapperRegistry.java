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

package org.gradle.api.internal.project;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A registry to synchronize the lifetime of wrappers with the projects they wrap.
 * Since {@link DefaultProject} instances are stored in various kinds of registries, they have a dedicated lifetime.
 * <p>
 * This lifetime is a part of public API. Consider a build logic that keeps a {@code WeakHashMap<Project, Object>} globally.
 * The lifetime of the keys here is important, because if keys are represented by ephemeral (collected on the next GC run) wrappers,
 * entries in the map will be collected earlier, compared to raw {@link DefaultProject} instances.
 * <p>
 * Implementations must be thread-safe.
 */
@ServiceScope(Scope.Build.class)
public interface ProjectWrapperRegistry {

    void register(ProjectInternal delegate, ProjectInternal wrapper);
}
