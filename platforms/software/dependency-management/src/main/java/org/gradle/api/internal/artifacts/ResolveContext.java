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
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolverFactory;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.component.model.DependencyMetadata;

import java.util.List;
import java.util.Set;

/**
 * Represents something that can be resolved.
 */
public interface ResolveContext extends DependencyMetaDataProvider {

    String getName();

    /**
     * Identifies this resolve context within a lockfile.
     */
    String getDependencyLockingId();

    ResolutionHost getResolutionHost();

    DomainObjectContext getDomainObjectContext();

    ResolutionStrategyInternal getResolutionStrategy();

    boolean hasDependencies();

    /**
     * @implSpec Usage: This method should only be called on resolvable configurations and should throw an exception if
     * called on a configuration that does not permit this usage.
     */
    RootComponentMetadataBuilder.RootComponentState toRootComponent();

    AttributeContainerInternal getAttributes();

    /**
     * @implSpec Usage: This method should only be called on resolvable configurations and should throw an exception if
     * called on a configuration that does not permit this usage.
     */
    TransformUpstreamDependenciesResolverFactory getDependenciesResolverFactory();

    /**
     * Returns the synthetic dependencies for this context. These dependencies are generated
     * by Gradle and not provided by the user, and are used for dependency locking and consistent resolution.
     * These constraints are not always used during resolution, based on which phase of execution we are in
     * (task dependencies, execution, ...)
     *
     * @implSpec Usage: This method should only be called on resolvable configurations and should throw an exception if
     * called on a configuration that does not permit this usage.
     */
    List<? extends DependencyMetadata> getSyntheticDependencies();

    /**
     * Marks this resolve context as observed, meaning its state has been seen by some external operation
     * and further changes to this context that would change its public state are forbidden.
     */
    void markAsObserved();

    FailureResolutions getFailureResolutions();

    /**
     * Details about this resolve context to provide additional context during failure cases.
     */
    interface FailureResolutions {

        /**
         * Provide resolutions to add to a failure to assist the user on resolving the provided
         * version conflicts.
         */
        List<String> forVersionConflict(Set<Conflict> conflicts);
    }
}
