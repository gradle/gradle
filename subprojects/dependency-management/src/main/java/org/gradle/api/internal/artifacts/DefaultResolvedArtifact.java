/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.Factory;

import java.io.File;

public class DefaultResolvedArtifact implements ResolvedArtifact {
    private final ResolvedModuleVersion owner;
    private final IvyArtifactName artifact;
    private long id;
    private Factory<File> artifactSource;
    private File file;

    public DefaultResolvedArtifact(ResolvedModuleVersion owner, IvyArtifactName artifact, Factory<File> artifactSource, long id) {
        this.owner = owner;
        this.artifact = artifact;
        this.id = id;
        this.artifactSource = artifactSource;
    }

    public long getId() {
        return id;
    }

    public ResolvedModuleVersion getModuleVersion() {
        return owner;
    }

    @Override
    public String toString() {
        return String.format("[ResolvedArtifact dependency:%s name:%s classifier:%s extension:%s type:%s]", owner, getName(), getClassifier(), getExtension(), getType());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultResolvedArtifact other = (DefaultResolvedArtifact) obj;
        if (!other.owner.getId().equals(owner.getId())) {
            return false;
        }
        if (!other.artifact.equals(artifact)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return owner.getId().hashCode() ^ getName().hashCode() ^ getType().hashCode() ^ getExtension().hashCode() ^ artifact.hashCode();
    }

    public String getName() {
        return artifact.getName();
    }

    public String getType() {
        return artifact.getType();
    }

    public String getExtension() {
        return artifact.getExtension();
    }

    public String getClassifier() {
        return artifact.getClassifier();
    }
    
    public File getFile() {
        if (file == null) {
            file = artifactSource.create();
            artifactSource = null;
        }
        return file;
    }
}
