/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.internal;

import com.google.common.collect.Lists;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.composite.internal.IncludedBuildTaskReference;
import org.gradle.initialization.BuildIdentity;
import org.gradle.initialization.ProjectPathRegistry;
import org.gradle.util.CollectionUtils;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

public class DefaultIdeArtifactRegistry implements IdeArtifactRegistry {
    private final IdeArtifactStore store;
    private final ProjectPathRegistry projectPathRegistry;
    private final DomainObjectContext domainObjectContext;
    private final BuildIdentity buildIdentity;
    private final FileOperations fileOperations;

    public DefaultIdeArtifactRegistry(IdeArtifactStore store, ProjectPathRegistry projectPathRegistry, FileOperations fileOperations, DomainObjectContext domainObjectContext, BuildIdentity buildIdentity) {
        this.store = store;
        this.projectPathRegistry = projectPathRegistry;
        this.fileOperations = fileOperations;
        this.domainObjectContext = domainObjectContext;
        this.buildIdentity = buildIdentity;
    }

    @Override
    public void registerIdeArtifact(final IdeProjectMetadata ideProjectMetadata) {
        ProjectComponentIdentifier projectId = newProjectId(buildIdentity.getCurrentBuild(), domainObjectContext.getProjectPath().getPath());
        store.put(projectId, ideProjectMetadata);
    }

    @Nullable
    @Override
    public <T extends IdeProjectMetadata> T getIdeArtifactMetadata(Class<T> type, ProjectComponentIdentifier project) {
        for (IdeProjectMetadata ideProjectMetadata : store.get(project)) {
            if (type.isInstance(ideProjectMetadata)) {
                return type.cast(ideProjectMetadata);
            }
        }
        return null;
    }

    @Override
    public <T extends IdeProjectMetadata> List<Reference<T>> getIdeArtifactMetadata(Class<T> type) {
        List<Reference<T>> result = Lists.newArrayList();
        for (Path projectPath : projectPathRegistry.getAllExplicitProjectPaths()) {
            final ProjectComponentIdentifier projectId = projectPathRegistry.getProjectComponentIdentifier(projectPath);
            for (IdeProjectMetadata ideProjectMetadata : store.get(projectId)) {
                if (type.isInstance(ideProjectMetadata)) {
                    final T metadata = type.cast(ideProjectMetadata);
                    // Need to use different APIs to reference a required task from outside the current build
                    // There should be one mechanism rather than two.
                    if (projectId.getBuild().equals(buildIdentity.getCurrentBuild())) {
                        result.add(new MetadataFromThisBuild<T>(metadata, projectId));
                    } else {
                        result.add(new MetadataFromOtherBuild<T>(metadata, projectId));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public FileCollection getIdeArtifacts(final Class<? extends IdeProjectMetadata> type) {
        return fileOperations.files(new Callable<List<FileCollection>>() {
            @Override
            public List<FileCollection> call() {
                return CollectionUtils.collect(
                    getIdeArtifactMetadata(type),
                    new Transformer<FileCollection, Reference<?>>() {
                        @Override
                        public FileCollection transform(Reference<?> result) {
                            ConfigurableFileCollection singleton = fileOperations.files(result.get().getFile());
                            singleton.builtBy(result.getBuildDependencies());
                            return singleton;
                        }
                    });
            }
        });
    }

    private static abstract class AbstractReference<T extends IdeProjectMetadata> implements Reference<T> {
        private final T metadata;
        private final ProjectComponentIdentifier projectId;

        AbstractReference(T metadata, ProjectComponentIdentifier projectId) {
            this.metadata = metadata;
            this.projectId = projectId;
        }

        @Override
        public T get() {
            return metadata;
        }

        @Override
        public ProjectComponentIdentifier getOwningProject() {
            return projectId;
        }
    }

    private static class MetadataFromThisBuild<T extends IdeProjectMetadata> extends AbstractReference<T> {
        MetadataFromThisBuild(T metadata, ProjectComponentIdentifier projectId) {
            super(metadata, projectId);
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return new AbstractTaskDependency() {
                @Override
                public void visitDependencies(TaskDependencyResolveContext context) {
                    for (Task task : get().getGeneratorTasks()) {
                        context.add(task);
                    }
                }
            };
        }
    }

    private static class MetadataFromOtherBuild<T extends IdeProjectMetadata> extends AbstractReference<T> {
        MetadataFromOtherBuild(T metadata, ProjectComponentIdentifier projectId) {
            super(metadata, projectId);
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return new AbstractTaskDependency() {
                @Override
                public void visitDependencies(TaskDependencyResolveContext context) {
                    for (Task task : get().getGeneratorTasks()) {
                        context.add(new IncludedBuildTaskReference(getOwningProject().getBuild().getName(), task.getPath()));
                    }
                }
            };
        }
    }
}
