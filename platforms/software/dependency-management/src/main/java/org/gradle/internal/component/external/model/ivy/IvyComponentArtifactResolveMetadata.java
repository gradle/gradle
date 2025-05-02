/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.external.model.ivy;

import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Metadata used to resolve artifacts of an Ivy component.
 */
public interface IvyComponentArtifactResolveMetadata extends ComponentArtifactResolveMetadata {
    /**
     * Get the artifacts for the given configuration.
     *
     * @return Null if the configuration does not exist.
     */
    @Nullable
    List<? extends ComponentArtifactMetadata> getConfigurationArtifacts(String configurationName);
}
