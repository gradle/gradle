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

package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

/**
 * An immutable identifier for an artifact that belongs to some module version.
 */
public interface ModuleComponentArtifactIdentifier extends ComponentArtifactIdentifier {
    /**
     * Returns the id of the component that this artifact belongs to.
     */
    @Override
    ModuleComponentIdentifier getComponentIdentifier();

    /**
     * Returns a file base name that can be used for this artifact.
     */
    String getFileName();

}
