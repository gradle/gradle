/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;

/**
 * The final representation of a component in the resolved dependency graph.
 * This is the type that is serialized on resolve and deserialized when we later need to build a `ResolutionResult`.
 */
public interface ResolvedGraphComponent {
    /**
     * Returns a simple id for this component, unique across components in the same graph.
     * This id cannot be used across graphs.
     */
    Long getResultId();

    /**
     * Returns a unique id for this component.
     */
    ComponentIdentifier getComponentId();

    /**
     * Returns the module version for this component.
     */
    ModuleVersionIdentifier getModuleVersion();

    /**
     * The reason this component was selected in the graph.
     */
    ComponentSelectionReason getSelectionReason();

    /**
     * Returns the name of the resolved variant. This can currently be 2 different things: for legacy components,
     * it's going to be the name of a "configuration" (either a project configuration, an Ivy configuration name or a Maven "scope").
     * For components with variants, it's going to be the name of the variant. This name is going to be used for reporting purposes.
     */
    DisplayName getVariantName();

    /**
     * Returns the attributes of the resolved variant. This is going to be used for reporting purposes. In practice, variant attributes
     * should effectively be what defines the _identity_ of the variant. In practice, because we have multiple kind of components, it's
     * not necessarily the case.
     */
    AttributeContainer getVariantAttributes();

    /**
     * Returns the name of the repository used to source this component, or {@code null} if this component was not resolved from a repository.
     */
    @Nullable
    String getRepositoryName();
}
