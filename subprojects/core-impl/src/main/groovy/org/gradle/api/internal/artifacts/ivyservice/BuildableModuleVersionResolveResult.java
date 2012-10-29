/*
 * Copyright 2012 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;

public interface BuildableModuleVersionResolveResult extends ModuleVersionResolveResult {
    void resolved(ModuleRevisionId moduleRevisionId, ModuleDescriptor descriptor, ArtifactResolver artifactResolver);

    void failed(ModuleVersionResolveException failure);

    /**
     * Replaces the meta-data in the result. Result must already be resolved.
     */
    void setMetaData(ModuleRevisionId moduleRevisionId, ModuleDescriptor descriptor);

    /**
     * Replaces the artifact resolver in the result. Result must already be resolved.
     */
    void setArtifactResolver(ArtifactResolver artifactResolver);
}
