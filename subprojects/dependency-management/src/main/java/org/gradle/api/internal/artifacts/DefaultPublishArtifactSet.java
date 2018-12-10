/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.DomainObjectProvider;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.DelegatingDomainObjectSet;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.provider.AbstractReadOnlyProvider;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultPublishArtifactSet extends DelegatingDomainObjectSet<PublishArtifact> implements PublishArtifactSet {
    private final TaskDependencyInternal builtBy = new ArtifactsTaskDependency();
    private final FileCollection files;
    private final Describable displayName;
    private final ObjectFactory objectFactory;
    private final TaskResolver taskResolver;

    @Inject
    public DefaultPublishArtifactSet(Describable displayName, DomainObjectSet<PublishArtifact> backingSet, FileCollectionFactory fileCollectionFactory, ObjectFactory objectFactory, TaskResolver taskResolver) {
        super(backingSet);
        this.displayName = displayName;
        this.files = fileCollectionFactory.create(builtBy, new ArtifactsFileCollection());
        this.objectFactory = objectFactory;
        this.taskResolver = taskResolver;
    }

    @Override
    public String toString() {
        return displayName.getDisplayName();
    }

    @Override
    public DomainObjectProvider<PublishArtifact> register(Provider<? extends FileSystemLocation> artifactFile, Action<? super ConfigurablePublishArtifact> configuration) {
        // TODO: Configure the configurable publish artifact based on FileSystemLocation initially?
        DefaultDomainObjectProvider provider = objectFactory.newInstance(DefaultDomainObjectProvider.class,
                artifactFile.map(file -> {
                    ConfigurablePublishArtifact artifact = objectFactory.newInstance(DefaultConfigurablePublishArtifact.class, objectFactory, taskResolver, artifactFile);
                    configuration.execute(artifact);
                    return artifact;
                })
        );
        addLater(provider);

        return provider;
    }

    private static class DefaultDomainObjectProvider extends AbstractReadOnlyProvider<PublishArtifact>  implements DomainObjectProvider<PublishArtifact> {
        private final Provider<PublishArtifact> delegate;

        private DefaultDomainObjectProvider(Provider<PublishArtifact> delegate) {
            this.delegate = delegate;
        }

        @Nullable
        @Override
        public Class<PublishArtifact> getType() {
            return PublishArtifact.class;
        }

        @Nullable
        @Override
        public PublishArtifact getOrNull() {
            return delegate.getOrNull();
        }
    }

    private static class DefaultConfigurablePublishArtifact implements ConfigurablePublishArtifact {
        private final Provider<? extends FileSystemLocation> artifactFile;
        private final DefaultTaskDependency taskDependency;
        private final Property<String> name;
        private final Property<String> classifier;
        private final Property<String> type;
        private final Property<String> extension;

        public DefaultConfigurablePublishArtifact(ObjectFactory objectFactory, TaskResolver resolver, Provider<? extends FileSystemLocation> artifactFile) {
            this.artifactFile = artifactFile;
            this.taskDependency = new DefaultTaskDependency(resolver, ImmutableSet.copyOf(Collections.singleton(artifactFile)));
            this.name = objectFactory.property(String.class);
            this.classifier = objectFactory.property(String.class);
            this.type = objectFactory.property(String.class);
            this.extension = objectFactory.property(String.class);
        }

        @Override
        public String getName() {
            // TODO: Figure out what the name should be
            return name.get();
        }

        @Override
        public void setName(String name) {
            this.name.set(name);
        }

        @Override
        public String getExtension() {
            return extension.get();
        }

        @Override
        public void setExtension(String extension) {
            this.name.set(name);
        }

        @Override
        public String getType() {
            return type.get();
        }

        @Override
        public void setType(String type) {
            this.name.set(name);
        }

        @Nullable
        @Override
        public String getClassifier() {
            return classifier.get();
        }

        @Override
        public void setClassifier(@Nullable String classifier) {
            this.classifier.set(classifier);
        }

        @Override
        public File getFile() {
            return artifactFile.get().getAsFile();
        }

        @Nullable
        @Override
        public Date getDate() {
            return new Date(getFile().lastModified());
        }

        @Override
        public ConfigurablePublishArtifact builtBy(Object... tasks) {
            throw new UnsupportedOperationException("Dependency information is extracted from Provider");
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return taskDependency;
        }
    }

    public FileCollection getFiles() {
        return files;
    }

    public TaskDependency getBuildDependencies() {
        return builtBy;
    }

    private class ArtifactsFileCollection implements MinimalFileSet {
        @Override
        public String getDisplayName() {
            return displayName.getDisplayName();
        }

        @Override
        public Set<File> getFiles() {
            Set<File> files = new LinkedHashSet<File>();
            for (PublishArtifact artifact : DefaultPublishArtifactSet.this) {
                files.add(artifact.getFile());
            }
            return files;
        }
    }

    private class ArtifactsTaskDependency extends AbstractTaskDependency {
        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            for (PublishArtifact publishArtifact : DefaultPublishArtifactSet.this) {
                context.add(publishArtifact);
            }
        }
    }
}
