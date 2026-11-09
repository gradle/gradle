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
import org.gradle.internal.DisplayName;

/**
 * An immutable identifier for an artifact that belongs to some module version.
 *
 * <p>
 * This must remain an interface, as third-party plugins (e.g. the Spring Boot plugin) reference it
 * and call its methods with {@code invokeinterface}.
 * Implementations should extend {@link AbstractModuleComponentArtifactIdentifier} so that equality is consistent between them.
 * </p>
 */
public interface ModuleComponentArtifactIdentifier extends ComponentArtifactIdentifier, DisplayName {
    /**
     * Returns the id of the component that this artifact belongs to.
     */
    @Override
    ModuleComponentIdentifier getComponentIdentifier();

    /**
     * The name of the file that the identified artifact is stored as locally.
     *
     * @implNote This is used as part of equality and hashing because downstream consumers may only use this name to identify the artifact.
     * We need to ensure that the same file stored in two different locations is treated as two different artifacts.
     */
    String getDestinationFileName();

    /**
     * The path of the identified artifact, relative to the directory that the module would use.
     *
     * <p>
     * Note that this may be assumed in some cases and not perfectly match the actual path used during resolution.
     * For example, Ivy repositories may use layouts that differ from the default layout, which must be used here
     * because identifiers are not aware of the owning repository.
     * </p>
     */
    String getFilePath();
}
