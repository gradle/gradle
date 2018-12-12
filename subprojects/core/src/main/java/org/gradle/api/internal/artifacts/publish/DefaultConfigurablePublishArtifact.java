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

package org.gradle.api.internal.artifacts.publish;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.util.SingleMessageLogger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Date;

public class DefaultConfigurablePublishArtifact implements ConfigurablePublishArtifact {
    private final Property<String> name;
    private final Property<String> extension;
    private final Property<String> type;
    private final Property<String> classifier;
    private final Provider<? extends FileSystemLocation> file;
    private final DefaultTaskDependency taskDependency;

    @Inject
    public DefaultConfigurablePublishArtifact(ObjectFactory objectFactory, TaskResolver resolver, Provider<? extends FileSystemLocation> file) {
        this.file = file;

        this.name = objectFactory.property(String.class).convention(file.map(new Transformer<String, FileSystemLocation>() {
            @Override
            public String transform(FileSystemLocation fileSystemLocation) {
                return fileSystemLocation.getAsFile().getName();
            }
        }));
        this.extension = objectFactory.property(String.class).convention(file.map(new Transformer<String, FileSystemLocation>() {
            @Override
            public String transform(FileSystemLocation fileSystemLocation) {
                return FilenameUtils.getExtension(fileSystemLocation.getAsFile().getName());
            }
        }));
        this.type = objectFactory.property(String.class).convention("");
        this.classifier = objectFactory.property(String.class).convention("");
        this.taskDependency = new DefaultTaskDependency(resolver, ImmutableSet.<Object>of(file));
    }

    @Override
    public Property<String> getArtifactName() {
        return name;
    }

    @Override
    public Property<String> getArtifactExtension() {
        return extension;
    }

    @Override
    public Property<String> getArtifactType() {
        return type;
    }

    @Override
    public Property<String> getArtifactClassifier() {
        return classifier;
    }

    @Override
    public void setName(String name) {
        this.name.set(name);
    }

    @Override
    public void setExtension(String extension) {
        this.extension.set(extension);
    }

    @Override
    public void setType(String type) {
        this.type.set(type);
    }

    @Override
    public void setClassifier(@Nullable String classifier) {
        this.classifier.set(classifier);
    }

    @Override
    public ConfigurablePublishArtifact builtBy(Object... tasks) {
        // TODO: This needs to remain configurable for some types of PublishArtifact (e.g., plain File)
        // SingleMessageLogger.nagUserOfDiscontinuedMethod("builtBy", "Dependency information for a published artifact should be derived from the Provider of the published file");
        taskDependency.add(tasks);
        return this;
    }

    @Override
    public String getName() {
        return name.getOrNull();
    }

    @Override
    public String getExtension() {
        return extension.getOrNull();
    }

    @Override
    public String getType() {
        return type.getOrNull();
    }

    @Nullable
    @Override
    public String getClassifier() {
        return classifier.getOrNull();
    }

    @Override
    public File getFile() {
        return file.get().getAsFile();
    }

    @Nullable
    @Override
    public Date getDate() {
        return new Date(getFile().lastModified());
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return taskDependency;
    }

    public PublishArtifact configureFor(final Provider<? extends AbstractArchiveTask> archiveTask) {
        // This is special casing we've done for a long time.
        getArtifactName().set(archiveTask.map(new Transformer<String, AbstractArchiveTask>() {
            @Override
            public String transform(AbstractArchiveTask archiveTask) {
                String baseName = archiveTask.getArchiveBaseName().getOrNull();
                String appendix = archiveTask.getArchiveAppendix().getOrNull();
                if (baseName != null && appendix != null) {
                    return baseName + "-" + appendix;
                }
                if (baseName != null) {
                    return baseName;
                }
                if (appendix != null) {
                    return appendix;
                }
                return archiveTask.getArchiveFileName().get();

            }
        }));
        getArtifactExtension().set(archiveTask.flatMap(new Transformer<Provider<String>, AbstractArchiveTask>() {
            @Override
            public Provider<String> transform(AbstractArchiveTask archiveTask) {
                return archiveTask.getArchiveExtension();
            }
        }));
        getArtifactType().set(getArtifactExtension());
        getArtifactClassifier().set(archiveTask.flatMap(new Transformer<Provider<String>, AbstractArchiveTask>() {
            @Override
            public Provider<String> transform(AbstractArchiveTask archiveTask) {
                return archiveTask.getArchiveClassifier();
            }
        }));
        return this;
    }
}
