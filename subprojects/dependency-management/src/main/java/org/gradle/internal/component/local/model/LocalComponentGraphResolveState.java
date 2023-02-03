/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveState;

import javax.annotation.concurrent.ThreadSafe;

/**
 * <p>Instances of this type are cached and reused for multiple graph resolutions, possibly in parallel. This means that the implementation must be thread-safe.
 *
 * <p>Currently, instances of this type are cached once per project per build in the tree (ie a copy is created for each build in the tree where the project is referenced as a dependency.
 * This is because some of the composite build infrastructure assumes a specialized copy per build. This should be changed to remove the need for multiple copies.</p>
 */
@ThreadSafe
public interface LocalComponentGraphResolveState extends ComponentGraphResolveState {
    ModuleVersionIdentifier getModuleVersionId();

    LocalComponentMetadata getArtifactMetadata();

    LocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts);
}
