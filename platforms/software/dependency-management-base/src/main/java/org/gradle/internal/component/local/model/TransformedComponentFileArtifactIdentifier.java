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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.DisplayName;

import java.util.Objects;

/**
 * Identifies the transformed artifact of a component. The original file name is tracked in order to guarantee uniqueness,
 * as artifact transformations may result in multiple artifacts with the same file name.
 *
 * <p>The original file name should refer to the name of the artifact at the beginning of the transform chain.</p>
 */
public class TransformedComponentFileArtifactIdentifier implements ComponentArtifactIdentifier, DisplayName {
    private final ComponentIdentifier componentId;
    private final String fileName;
    private final String originalFileName;

    public TransformedComponentFileArtifactIdentifier(ComponentIdentifier componentId, String fileName, String originalFileName) {
        this.componentId = componentId;
        this.fileName = fileName;
        this.originalFileName = originalFileName;
    }

    @Override
    public ComponentIdentifier getComponentIdentifier() {
        return componentId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    @Override
    public String getDisplayName() {
        return getOriginalFileName() + " -> " + getFileName() + " (" + getComponentIdentifier().getDisplayName() + ")";
    }

    @Override
    public String getCapitalizedDisplayName() {
        return getDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        TransformedComponentFileArtifactIdentifier other = (TransformedComponentFileArtifactIdentifier) obj;
        return componentId.equals(other.componentId) && fileName.equals(other.fileName) && originalFileName.equals(other.originalFileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentId, fileName, originalFileName);
    }
}
