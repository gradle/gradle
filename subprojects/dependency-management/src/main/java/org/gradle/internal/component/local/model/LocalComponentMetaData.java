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

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.internal.component.external.model.BuildableIvyModulePublishMetaData;
import org.gradle.internal.component.model.ComponentArtifactIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetaData;

public interface LocalComponentMetaData {
    ModuleVersionIdentifier getId();

    /**
     * Converts this component to resolve meta-data.
     */
    ComponentResolveMetaData toResolveMetaData();

    /**
     * Converts this component to publication meta-data.
     */
    BuildableIvyModulePublishMetaData toPublishMetaData();

    @Nullable
    LocalArtifactMetaData getArtifact(ComponentArtifactIdentifier artifactIdentifier);
}
