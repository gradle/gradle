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
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.component.local.model.TransformedComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueFactory;

import javax.annotation.Nullable;
import java.io.File;

public class PreResolvedResolvableArtifact implements ResolvableArtifact {
    private final ModuleVersionIdentifier owner;
    private final IvyArtifactName artifact;
    private final ComponentArtifactIdentifier artifactId;
    private final File file;
    private final CalculatedValue<File> fileSource;
    private final TaskDependencyContainer builtBy;
    private final CalculatedValueFactory calculatedValueFactory;
    private final DefaultResolvedArtifact publicView;

    public PreResolvedResolvableArtifact(@Nullable ModuleVersionIdentifier owner, IvyArtifactName artifact, ComponentArtifactIdentifier artifactId, File file, TaskDependencyContainer builtBy, CalculatedValueFactory calculatedValueFactory) {
        this.owner = owner;
        this.artifact = artifact;
        this.artifactId = artifactId;
        this.file = file;
        this.fileSource = calculatedValueFactory.create(Describables.of(artifactId), file);
        this.builtBy = builtBy;
        this.calculatedValueFactory = calculatedValueFactory;
        this.publicView = new DefaultResolvedArtifact(artifactId, fileSource, owner, artifact);
    }

    @Override
    public int hashCode() {
        return artifactId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        PreResolvedResolvableArtifact other = (PreResolvedResolvableArtifact) obj;
        return other.artifactId.equals(artifactId);
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return artifactId;
    }

    @Override
    public CalculatedValue<File> getFileSource() {
        return fileSource;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public ResolvedArtifact toPublicView() {
        return publicView;
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
    public ResolvableArtifact transformedTo(File file) {
        IvyArtifactName artifactName = DefaultIvyArtifactName.forFile(file, artifact.getClassifier());

        String originalFileName;
        if (artifactId instanceof TransformedComponentFileArtifactIdentifier) {
            originalFileName = ((TransformedComponentFileArtifactIdentifier) artifactId).getOriginalFileName();
        } else {
            originalFileName = this.file.getName();
        }

        ComponentArtifactIdentifier newId = new TransformedComponentFileArtifactIdentifier(artifactId.getComponentIdentifier(), file.getName(), originalFileName);
        return new PreResolvedResolvableArtifact(owner, artifactName, newId, file, builtBy, calculatedValueFactory);
    }

    @Override
    public IvyArtifactName getArtifactName() {
        return artifact;
    }
}
