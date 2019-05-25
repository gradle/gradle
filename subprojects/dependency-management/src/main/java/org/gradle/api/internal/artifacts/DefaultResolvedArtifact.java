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

import org.gradle.api.Task;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.tasks.FinalizeAction;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;

public class DefaultResolvedArtifact implements ResolvedArtifact, ResolvableArtifact {
    private final ModuleVersionIdentifier owner;
    private final IvyArtifactName artifact;
    private final ComponentArtifactIdentifier artifactId;
    private final TaskDependency buildDependencies;
    private volatile Factory<File> artifactSource;
    private final ResolvableArtifact sourceArtifact;
    private volatile File file;
    private volatile Throwable failure;

    public DefaultResolvedArtifact(ModuleVersionIdentifier owner, IvyArtifactName artifact, ComponentArtifactIdentifier artifactId, TaskDependency buildDependencies, Factory<File> artifactSource) {
        this.owner = owner;
        this.artifact = artifact;
        this.artifactId = artifactId;
        this.buildDependencies = buildDependencies;
        this.sourceArtifact = null;
        this.artifactSource = artifactSource;
    }

    public DefaultResolvedArtifact(ModuleVersionIdentifier owner, IvyArtifactName artifact, ComponentArtifactIdentifier artifactId, ResolvableArtifact sourceArtifact, File artifactFile) {
        this.owner = owner;
        this.artifact = artifact;
        this.artifactId = artifactId;
        this.buildDependencies = null;
        this.sourceArtifact = sourceArtifact;
        this.file = artifactFile;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(new FinalizeAction() {
            @Override
            public TaskDependencyContainer getDependencies() {
                return new TaskDependencyContainer() {
                    @Override
                    public void visitDependencies(TaskDependencyResolveContext context) {
                        if (buildDependencies != null) {
                            context.add(buildDependencies);
                        } else if (sourceArtifact != null) {
                            context.add(sourceArtifact);
                        }
                    }
                };
            }

            @Override
            public void execute(Task task) {
                // Eagerly calculate the file if this will be used as a dependency of some task
                // This is to avoid having to lock the project when a consuming task in another project runs
                if (isResolveSynchronously()) {
                    try {
                        getFile();
                    } catch (Exception e) {
                        // Ignore, this will be reported later
                    }
                }
            }
        });
    }

    @Override
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
    public boolean isResolveSynchronously() {
        if (artifactId.getComponentIdentifier() instanceof ProjectComponentIdentifier) {
            // Don't bother resolving local components asynchronously
            return true;
        }
        return file != null || failure != null;
    }

    @Override
    public File getFile() {
        // This method tries to minimize the number of volatile read/writes.
        // Do NOT try to inline the variables there.
        File f = file;

        if (f == null) {
            Throwable err = failure;
            if (err != null) {
                throw UncheckedException.throwAsUncheckedException(err);
            }
            try {
                f = artifactSource.create();
                file = f;
            } catch (Exception e) {
                err = e;
                failure = err;
                throw UncheckedException.throwAsUncheckedException(err);
            }
        }
        return f;
    }
}
