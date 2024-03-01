/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.component.local.model;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;

import javax.annotation.Nullable;

public interface LocalComponentMetadata extends ComponentResolveMetadata, ComponentGraphResolveMetadata {
    @Nullable
    @Override
    LocalConfigurationMetadata getConfiguration(String name);

    LocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformer);

    /**
     * We currently allow a configuration that has been partially observed for resolution to be modified
     * in a beforeResolve callback.
     *
     * To reduce the number of instances of root component metadata we create, we mark all configurations
     * as dirty and in need of re-evaluation when we see certain types of modifications to a configuration.
     *
     * In the future, we could narrow the number of configurations that need to be re-evaluated, but it would
     * be better to get rid of the behavior that allows configurations to be modified once they've been observed.
     *
     * @see org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder.MetadataHolder#tryCached(ComponentIdentifier)
     */
    void reevaluate();

    /**
     * Returns if the configuration with the given name has been realized.
     */
    @VisibleForTesting
    boolean isConfigurationRealized(String configName);
}
