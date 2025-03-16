/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.enterprise;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Features for Develocity used during settings configuration to register lifecycle callbacks.
 * <p>
 * This service is for lifecycle callbacks that don't have a stable API, yet,
 * to not break the Develocity plugin going forward.
 *
 * @since 8.8
 */
@ServiceScope(Scope.Build.class)
public interface DevelocityBuildLifecycleService {

    /**
     * Adds an action to be called immediately before a project is evaluated.
     * <p>
     * The action will be applied to all projects configured in the current build. Due to `Isolated Projects`, a particular build might only configure a subset of all projects.
     *
     * @param action The action to execute.
     */
    void beforeProject(Action<? super Project> action);

}
