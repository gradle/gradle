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

import org.gradle.api.Buildable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;

public class DefaultResolvedArtifact implements ResolvedArtifact, Buildable, ResolvableArtifact {
    private final ModuleVersionIdentifier owner;
    private final IvyArtifactName artifact;
    private final ComponentArtifactIdentifier artifactId;
    private final TaskDependency buildDependencies;
    private volatile Factory<File> artifactSource;
    private File file;
    private Throwable failure;

    public DefaultResolvedArtifact(ModuleVersionIdentifier owner, IvyArtifactName artifact, ComponentArtifactIdentifier artifactId, TaskDependency buildDependencies, Factory<File> artifactSource) {
        this.owner = owner;
        this.artifact = artifact;
        this.artifactId = artifactId;
        this.buildDependencies = buildDependencies;
        this.artifactSource = artifactSource;
    }

    public DefaultResolvedArtifact(ModuleVersionIdentifier owner, IvyArtifactName artifact, ComponentArtifactIdentifier artifactId, TaskDependency buildDependencies, File artifactFile) {
        this.owner = owner;
        this.artifact = artifact;
        this.artifactId = artifactId;
        this.buildDependencies = buildDependencies;
        this.file = artifactFile;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    public ResolvedModuleVersion getModuleVersion() {
        return new DefaultResolvedModuleVersion(owner);
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return artifactId;
    }

    @Override
    public String toString() {
        return artifactId.getDisplayName();
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
        return other.owner.equals(owner) && other.artifactId.equals(artifactId);
    }

    @Override
    public int hashCode() {
        return owner.hashCode() ^ artifactId.hashCode();
    }

    @Override
    public String getName() {
        return artifact.getName();
    }

    @Override
    public String getType() {
        return artifact.getType();
    }

    @Override
    public String getExtension() {
        return artifact.getExtension();
    }

    @Override
    public String getClassifier() {
        return artifact.getClassifier();
    }

    @Override
    public ResolvedArtifact toPublicView() {
        return this;
    }

    @Override
    public boolean isResolved() {
        synchronized (this) {
            return file != null || failure != null;
        }
    }

    @Override
    public File getFile() {
        synchronized (this) {
            if (file != null) {
                return file;
            }
            if (failure != null) {
                throw UncheckedException.throwAsUncheckedException(failure);
            }
            try {
                file = artifactSource.create();
                return file;
            } catch (Throwable e) {
                failure = e;
                throw UncheckedException.throwAsUncheckedException(failure);
            } finally {
                artifactSource = null;
            }
        }
    }
}
