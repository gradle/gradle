/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileCollection;

/**
 * Represents a local file dependency. Should be modelled as a regular dependency, but is treated separately as a migration step.
 */
public interface LocalFileDependencyMetadata {
    /**
     * Returns the id of the component that the file dependency references, if known. If not known an arbitrary identifier will be assigned.
     */
    @Nullable
    ComponentIdentifier getComponentId();

    /**
     * Remove this.
     */
    FileCollectionDependency getSource();

    FileCollection getFiles();
}
