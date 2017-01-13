/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.Nullable;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;

import java.io.File;

/**
 * Information about a resolved artifact.
 */
public interface ResolvedArtifact extends HasAttributes {
    File getFile();

    /**
     * Returns the module which this artifact belongs to.
     *
     * @return The module.
     */
    ResolvedModuleVersion getModuleVersion();

    String getName();

    String getType();

    String getExtension();

    @Nullable
    String getClassifier();

    @Incubating
    ComponentArtifactIdentifier getId();
}
