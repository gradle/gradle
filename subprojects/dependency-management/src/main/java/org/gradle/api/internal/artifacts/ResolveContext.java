/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.transform.ExtraExecutionGraphDependenciesResolverFactory;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.util.Path;

import java.util.List;

/**
 * Represents something that can be resolved.
 */
public interface ResolveContext extends DependencyMetaDataProvider {

    String getName();

    String getDisplayName();

    Path getIdentityPath();

    Path getProjectPath();

    DomainObjectContext getDomainObjectContext();

    ResolutionStrategyInternal getResolutionStrategy();

    boolean hasDependencies();

    LocalComponentMetadata toRootComponentMetaData();

    AttributeContainerInternal getAttributes();

    ExtraExecutionGraphDependenciesResolverFactory getDependenciesResolver();

    /**
     * Returns the synthetic dependencies for this context. These dependencies are generated
     * by Gradle and not provided by the user, and are used for dependency locking and consistent resolution.
     * These constraints are not always used during resolution, based on which phase of execution we are in
     * (task dependencies, execution, ...)
     */
    List<? extends DependencyMetadata> getSyntheticDependencies();

    /**
     * This method is a heuristic that gives an idea of the "size" of the graph. The larger
     * the graph is, the higher the risk of internal resizes exists, so we try to estimate
     * the size of the graph to avoid maps resizing.
     */
    int getEstimatedGraphSize();
}
