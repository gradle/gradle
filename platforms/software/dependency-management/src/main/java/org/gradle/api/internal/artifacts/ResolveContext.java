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

import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Represents something that can be resolved.
 */
public interface ResolveContext {

    String getName();

    /**
     * The identity of this resolve context, if it is a configuration.
     * <p>
     * Currently, everything that can be resolved is a configuration, but
     * this is likely to change in the future when we introduce new APIs
     * to perform resolution.
     * <p>
     * Used by artifact transforms to identify the source configuration in
     * build operations.
     */
    @Nullable
    ConfigurationIdentity getConfigurationIdentity();

    /**
     * Identifies this resolve context within a lockfile.
     */
    String getDependencyLockingId();

    ResolutionHost getResolutionHost();

    ResolutionStrategyInternal getResolutionStrategy();

    /**
     * @implSpec Usage: This method should only be called on resolvable configurations and should throw an exception if
     * called on a configuration that does not permit this usage.
     */
    RootComponentMetadataBuilder.RootComponentState toRootComponent();

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
