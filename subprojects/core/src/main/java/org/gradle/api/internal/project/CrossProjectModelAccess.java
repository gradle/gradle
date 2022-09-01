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

package org.gradle.api.internal.project;

import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Mediates access across project boundaries.
 */
@ServiceScope(Scopes.Build.class)
public interface CrossProjectModelAccess {
    /**
     * Locates the given project relative to some project.
     *
     * @param referrer The project from which the return value will be used.
     * @param path absolute path
     */
    @Nullable
    ProjectInternal findProject(ProjectInternal referrer, ProjectInternal relativeTo, String path);

    /**
     * @param referrer The project from which the return value will be used.
     */
    ProjectInternal access(ProjectInternal referrer, ProjectInternal project);

    /**
     * @param referrer The project from which the return value will be used.
     */
    Set<? extends ProjectInternal> getSubprojects(ProjectInternal referrer, ProjectInternal relativeTo);

    /**
     * @param referrer The project from which the return value will be used.
     */
    Set<? extends ProjectInternal> getAllprojects(ProjectInternal referrer, ProjectInternal relativeTo);

    /**
     * Produces a {@code DynamicObject} for the inherited scope from the parent project of the specified project, behaving correctly
     * regarding cross-project model access.
     *
     * @param referrerProject The project that needs to get an inherited scope dynamic object from its parent.
     * @return Returns a {@code DynamicObject} for the {@code referrerProject}'s parent project, or null if there is no parent project.
     * The returned object handles cross-project model access according to the current policy.
     */
    @Nullable
    DynamicObject parentProjectDynamicInheritedScope(ProjectInternal referrerProject);
}
