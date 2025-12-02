/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * A registry of all projects in a build, accessible by path.
 * <p>
 * <strong>This type should be avoided if possible.</strong> Prefer {@link ProjectStateRegistry} or
 * {@link org.gradle.internal.build.BuildProjectRegistry}, which operate on {@link ProjectState}
 * instances instead of raw {@link ProjectInternal} instances.
 */
@ServiceScope(Scope.Build.class)
@UsedByScanPlugin("ImportJUnitXmlReports")
public interface ProjectRegistry extends HoldsProjectState {

    void addProject(ProjectInternal project);

    // This is only here because it is used by the Develocity plugin in ImportJUnitXmlReports.
    // The ProjectRegistry and ProjectIdentifier types are legacy and should be
    // removed once we no longer support Develocity plugins that use this API.
    @Deprecated
    @UsedByScanPlugin("ImportJUnitXmlReports")
    @Nullable ProjectIdentifier getProject(String path);

    /**
     * Prefer {@link ProjectStateRegistry#findProjectState(Path)}.
     */
    @Nullable ProjectInternal getProjectInternal(String path);

    Set<ProjectInternal> getAllProjects(String path);

    Set<ProjectInternal> getSubProjects(String path);

}
