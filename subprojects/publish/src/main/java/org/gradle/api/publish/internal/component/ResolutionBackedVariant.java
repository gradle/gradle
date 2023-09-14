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

package org.gradle.api.publish.internal.component;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.DependencyMappingDetails;
import org.gradle.api.component.SoftwareComponentVariant;

import javax.annotation.Nullable;

/**
 * A {@link SoftwareComponentVariant} which is optionally backed by resolution during publication.
 * If enabled, the resolution configuration is resolved in order to override declared dependencies
 * with the resolved coordinates and versions.
 */
public interface ResolutionBackedVariant extends SoftwareComponentVariant {

    /**
     * See {@link DependencyMappingDetails#getPublishResolvedCoordinates()}
     */
    boolean getPublishResolvedCoordinates();

    /**
     * See {@link DependencyMappingDetails#getResolutionConfiguration()}
     */
    @Nullable
    Configuration getResolutionConfiguration();
}
