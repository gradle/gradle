/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.shareddata;

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

/**
 * Provides the ability for project configuration logic to register shared data produced by the project and to obtain shared data
 * that is produced by other projects.
 *
 * @since 8.6
 */
@Incubating
public interface ProjectSharedData {
    /**
     * TBD
     * @since 8.6
     */
    <T> void register(Class<T> dataType, @Nullable String dataIdentifier, Provider<T> dataProvider);

    /**
     * TBD
     * @since 8.6
     */
    <T> Provider<T> obtain(Class<T> dataType, @Nullable String dataIdentifier, SingleSourceIdentifier dataSourceIdentifier);

    /**
     * TBD
     * @since 8.6
     */
    <T> Provider<T> obtain(Class<T> dataType, SingleSourceIdentifier dataSourceIdentifier);

    /**
     * TBD
     * @since 8.6
     */
    <T> Provider<Map<String, ? extends T>> obtain(Class<T> dataType, String dataIdentifier, MultipleSourcesIdentifier dataSourceIdentifier);

    /**
     * TBD
     * @since 8.6
     */
    // TODO: these should conveniently wrap heterogeneous project identifiers, to be used as `sharedData.obtainSharedData(..., sharedData.fromProject(id))`
    SingleSourceIdentifier fromProject(Project project);

    /**
     * TBD
     * @since 8.6
     */
    SingleSourceIdentifier fromProject(ProjectComponentIdentifier project);

    /**
     * TBD
     * @since 8.6
     */
    MultipleSourcesIdentifier fromProjects(Collection<Project> projects);

    // TODO API shape issue? giving the project instance to the user code is prone to violations of isolation (which we catch, but still)

    /**
     * TBD
     * @since 8.6
     */
    MultipleSourcesIdentifier fromAllProjects();

    /**
     * TBD
     * @since 8.6
     */
    MultipleSourcesIdentifier fromProjectsInCurrentBuild();

    /**
     * TBD
     * @since 8.6
     */
    MultipleSourcesIdentifier fromAllProjectsMatchingIdentityPath(Spec<? super String> filterProjects);

    /**
     * TBD
     * @since 8.6
     */
    MultipleSourcesIdentifier fromResolutionResults(Configuration configuration);

}
