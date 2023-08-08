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

package org.gradle.internal.component.local.model;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Transformer;
import org.gradle.internal.component.model.ConfigurationMetadata;

public interface LocalConfigurationMetadata extends ConfigurationMetadata, LocalConfigurationGraphResolveMetadata {

    @Override
    ImmutableList<? extends LocalComponentArtifactMetadata> getArtifacts();

    /**
     * Returns a copy of this configuration metadata, except with all artifacts transformed by the given transformer.
     *
     * @param artifactTransformer A transformer applied to all artifacts and sub-variant artifacts.
     *
     * @return A copy of this metadata, with the given transformer applied to all artifacts.
     */
    LocalConfigurationMetadata copyWithTransformedArtifacts(Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer);

}
