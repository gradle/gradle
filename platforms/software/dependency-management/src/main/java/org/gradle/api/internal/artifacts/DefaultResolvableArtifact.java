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

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.internal.component.local.model.TransformedComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueFactory;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultResolvableArtifact implements ResolvableArtifact {
    private final ModuleVersionIdentifier owner;
    private final IvyArtifactName artifact;
    private final ComponentArtifactIdentifier artifactId;
    private final TaskDependencyContainer buildDependencies;
    private final CalculatedValue<File> fileSource;
    private final WorkNodeAction resolvedArtifactDependency;
    private final CalculatedValueFactory calculatedValueFactory;
    private final ResolvedArtifact publicView;

    public DefaultResolvableArtifact(@Nullable ModuleVersionIdentifier owner, IvyArtifactName artifact, ComponentArtifactIdentifier artifactId, TaskDependencyContainer builtBy, CalculatedValue<File> fileSource, CalculatedValueFactory calculatedValueFactory) {
        this.owner = owner;
        this.artifact = artifact;
        this.artifactId = artifactId;
        this.buildDependencies = builtBy;
        this.fileSource = fileSource;
        if (isProjectArtifact()) {
            // Use a node to eagerly calculate the file if this artifact will be used as a dependency of some other node
            // This is to avoid having to lock the producing project when a consuming task in another project runs
            this.resolvedArtifactDependency = new ResolveAction(this);
        } else {
            this.resolvedArtifactDependency = null;
        }
        this.calculatedValueFactory = calculatedValueFactory;
        this.publicView = new DefaultResolvedArtifact(artifactId, fileSource, owner, artifact);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(buildDependencies);
        if (resolvedArtifactDependency != null) {
            context.add(resolvedArtifactDependency);
        }
    }

    @Override
    public IvyArtifactName getArtifactName() {
        return artifact;
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return artifactId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultResolvableArtifact other = (DefaultResolvableArtifact) obj;
        return other.artifactId.equals(artifactId);
    }

    @Override
    public int hashCode() {
        return artifactId.hashCode();
    }

    @Override
    public ResolvedArtifact toPublicView() {
        return publicView;
    }

    @Override
    public ResolvableArtifact transformedTo(File file) {
        IvyArtifactName artifactName = DefaultIvyArtifactName.forFile(file, artifact.getClassifier());

        String originalFileName;
        if (artifactId instanceof TransformedComponentFileArtifactIdentifier) {
            originalFileName = ((TransformedComponentFileArtifactIdentifier) artifactId).getOriginalFileName();
        } else {
            originalFileName = fileSource.get().getName();
        }

        ComponentArtifactIdentifier newId = new TransformedComponentFileArtifactIdentifier(artifactId.getComponentIdentifier(), file.getName(), originalFileName);
        return new PreResolvedResolvableArtifact(owner, artifactName, newId, file, TaskDependencyContainer.EMPTY, calculatedValueFactory);
    }

    @Override
    public boolean isResolveSynchronously() {
        if (isProjectArtifact()) {
            // Don't bother resolving local components asynchronously
            return true;
        }
        return fileSource.isFinalized();
    }

    private boolean isProjectArtifact() {
        return artifactId.getComponentIdentifier() instanceof ProjectComponentIdentifier;
    }

    @Override
    public CalculatedValue<File> getFileSource() {
        return fileSource;
    }

    @Override
    public File getFile() {
        fileSource.finalizeIfNotAlready();
        return fileSource.get();
    }

    public static class ResolveAction implements WorkNodeAction {
        private final DefaultResolvableArtifact artifact;

        public ResolveAction(DefaultResolvableArtifact artifact) {
            this.artifact = artifact;
        }

        @Override
        public String toString() {
            return "resolve " + artifact.artifactId;
        }

        public DefaultResolvableArtifact getArtifact() {
            return artifact;
        }

        @Override
        public boolean usesMutableProjectState() {
            return true;
        }

        @Nullable
        @Override
        public Project getOwningProject() {
            if (artifact.fileSource.getResourceToLock() instanceof ProjectState) {
                return ((ProjectState) artifact.fileSource.getResourceToLock()).getMutableModel();
            } else {
                return null;
            }
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(artifact.buildDependencies);
        }

        @Override
        public void run(NodeExecutionContext context) {
            artifact.fileSource.finalizeIfNotAlready();
        }
    }
}
