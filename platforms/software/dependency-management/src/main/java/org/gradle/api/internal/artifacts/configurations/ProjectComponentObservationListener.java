/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.StatefulListener;
import org.gradle.util.Path;

import javax.annotation.Nullable;

/**
 * A listener that is notified when one project observes the local component metadata of another.
 */
@StatefulListener
@EventScope(Scopes.Build.class)
public interface ProjectComponentObservationListener {
    /**
     * Called when one project observes the local component metadata of another project.
     *
     * @param consumingProjectPath The path of the project performing the observation. Null if the consuming resolution is not a project.
     * @param targetProjectPath The path of the project that is being observed.
     */
    void projectObserved(@Nullable Path consumingProjectPath, Path targetProjectPath);
}
