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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;

import javax.annotation.Nullable;

/**
 * <p>A {@code Module} represents the meta-information about a project which should be used when publishing the
 * module.</p>
 */
public interface Module {
    String DEFAULT_STATUS = "integration";

    /**
     * Get the ID of the project that owns this module.
     */
    @Nullable
    ProjectComponentIdentifier getOwner();

    /**
     * Get this module's componentId, if it is a project component ID. This may be null while
     * {@link #getOwner} is not if a project owns a given module, but the module is not a
     * project component. For example, detached configurations are owned by a project but are
     * not project components.
     *
     * TODO: This should return a ComponentIdentifier and should not be nullable. But,
     * since the implementations of this interface live in :core, they cannot access the
     * constructor to ModuleComponentIdentifier. We should move Module and DependencyMetadataProvider
     * to :dependency-management and fix this.
     */
    @Nullable
    ProjectComponentIdentifier getComponentId();

    String getGroup();

    String getName();

    String getVersion();

    String getStatus();
}
