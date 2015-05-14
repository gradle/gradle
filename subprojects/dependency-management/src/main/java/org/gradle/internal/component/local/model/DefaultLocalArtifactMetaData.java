/*
 * Copyright 2015 the original author or authors.
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

import org.apache.commons.lang.ObjectUtils;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentArtifactIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;

class DefaultLocalArtifactMetaData implements LocalArtifactMetaData {
    private final DefaultLocalArtifactIdentifier id;
    private final File file;

    DefaultLocalArtifactMetaData(ComponentIdentifier componentIdentifier, String displayName, IvyArtifactName artifact, File file) {
        this.id = new DefaultLocalArtifactIdentifier(componentIdentifier, displayName, artifact, file);
        this.file = file;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public IvyArtifactName getName() {
        return id.getName();
    }

    public ComponentIdentifier getComponentId() {
        return id.getComponentIdentifier();
    }

    public ComponentArtifactIdentifier getId() {
        return id;
    }

    public File getFile() {
        return file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultLocalArtifactMetaData that = (DefaultLocalArtifactMetaData) o;
        return id.equals(that.id) && ObjectUtils.equals(file, that.file);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + ObjectUtils.hashCode(file);
        return result;
    }
}
