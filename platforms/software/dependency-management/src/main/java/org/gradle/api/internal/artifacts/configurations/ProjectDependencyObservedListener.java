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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedProjectConfiguration;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.StatefulListener;

import javax.annotation.Nullable;

@StatefulListener
@EventScope(Scopes.Build.class)
public interface ProjectDependencyObservedListener {
    /**
     * Called when a configuration of a project is consumed as a dependency.
     */
    void dependencyObserved(@Nullable ProjectState consumingProject, ProjectState targetProject, ConfigurationInternal.InternalState requestedState, ResolvedProjectConfiguration target);
}
