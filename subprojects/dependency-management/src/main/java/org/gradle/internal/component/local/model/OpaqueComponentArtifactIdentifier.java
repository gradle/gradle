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

import java.io.File;

public class OpaqueComponentArtifactIdentifier implements ComponentArtifactIdentifier, ComponentIdentifier {
    private final File file;

    public OpaqueComponentArtifactIdentifier(File file) {
        this.file = file;
    }

    @Override
    public ComponentIdentifier getComponentIdentifier() {
        return this;
    }

    @Override
    public String getDisplayName() {
        return file.getName();
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
        OpaqueComponentArtifactIdentifier other = (OpaqueComponentArtifactIdentifier) obj;
        return file.equals(other.file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }
}
