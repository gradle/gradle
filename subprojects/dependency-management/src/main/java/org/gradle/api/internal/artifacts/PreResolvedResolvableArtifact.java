/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
import java.io.File;

public class PreResolvedResolvableArtifact implements ResolvableArtifact, ResolvedArtifact {
    private final ModuleVersionIdentifier owner;
    private final IvyArtifactName artifact;
    private final ComponentArtifactIdentifier artifactId;
    private final File file;
    private final TaskDependencyContainer builtBy;

    public PreResolvedResolvableArtifact(@Nullable ModuleVersionIdentifier owner, IvyArtifactName artifact, ComponentArtifactIdentifier artifactId, File file, TaskDependencyContainer builtBy) {
        this.owner = owner;
        this.artifact = artifact;
        this.artifactId = artifactId;
        this.file = file;
        this.builtBy = builtBy;
    }

    @Override
    public String toString() {
        return artifactId.getDisplayName();
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return artifactId;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public ResolvedArtifact toPublicView() {
        return this;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        builtBy.visitDependencies(context);
    }

    @Override
    public boolean isResolveSynchronously() {
        return true;
    }

    @Override
    public ResolvedModuleVersion getModuleVersion() {
        if (owner == null) {
            // Local file dependencies do not have an owner
            throw new UnsupportedOperationException();
        }
        return new DefaultResolvedModuleVersion(owner);
    }

    @Override
    public ResolvableArtifact transformedTo(File file) {
        IvyArtifactName artifactName = DefaultIvyArtifactName.forFile(file, getClassifier());
        ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(artifactId.getComponentIdentifier(), artifactName);
        return new PreResolvedResolvableArtifact(owner, artifactName, newId, file, builtBy);
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
        return artifact.getType();
    }

    @Nullable
    @Override
    public String getClassifier() {
        return artifact.getClassifier();
    }
}
