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

package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;

/**
 * A component file artifact identifier that tracks the original file name.
 *
 * TODO: Rename to TransformedComponentFileArtifactIdentifier and
 *  rename TransformedComponentFileArtifactIdentifier to DefaultTransformedComponentFileArtifactIdentifier
 */
public interface ComponentFileArtifactIdentifierWithOriginal extends ComponentArtifactIdentifier {

    /**
     * Returns the file name of the artifact.
     */
    String getFileName();

    /**
     * Returns the original file name of the artifact.
     */
    String getOriginalFileName();
}
