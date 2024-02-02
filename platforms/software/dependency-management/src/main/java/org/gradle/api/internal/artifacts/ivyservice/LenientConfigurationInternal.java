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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec;

/**
 * Internal counterpart of {@link LenientConfiguration}.
 */
public interface LenientConfigurationInternal extends LenientConfiguration, ResolverResults.LegacyResolverResults.LegacyVisitedArtifactSet {

    /**
     * An artifact selection spec that can be applied to a visited artifact set to select
     * the artifacts that a configuration's intrinsic file collection would select.
     */
    ArtifactSelectionSpec getImplicitSelectionSpec();
}
