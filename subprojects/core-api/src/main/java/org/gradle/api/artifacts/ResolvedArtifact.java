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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * Information about a resolved artifact.
 * @since 0.8
 */
public interface ResolvedArtifact {
    /**
     * Returns the local file for this artifact. Downloads the artifact if not already available locally, blocking until complete.
     * @since 0.8
     */
    File getFile();

    /**
     * Returns the module which this artifact belongs to.
     *
     * @return The module.
     * @since 1.0
     */
    ResolvedModuleVersion getModuleVersion();

    /**
     * Returns the name.
     *
     * @since 0.8
     */
    String getName();

    /**
     * Returns the type.
     *
     * @since 0.8
     */
    String getType();

    /**
     * Returns the extension.
     *
     * @since 0.8
     */
    @Nullable
    String getExtension();

    /**
     * Returns the classifier.
     *
     * @since 1.0
     */
    @Nullable
    String getClassifier();

    /**
     * Returns the id.
     *
     * @since 2.6
     */
    ComponentArtifactIdentifier getId();
}
