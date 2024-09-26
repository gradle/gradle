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

package org.gradle.api.publish.ivy.internal.artifact;

import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nullable;

public class SingleOutputTaskIvyArtifact extends AbstractIvyArtifact {

    private final TaskProvider<? extends Task> generator;
    private final IvyPublicationCoordinates coordinates;
    private final String defaultExtension;
    private final String defaultType;
    private final String defaultClassifier;
    private final TaskDependencyInternal buildDependencies;

    public SingleOutputTaskIvyArtifact(
        TaskProvider<? extends Task> generator,
        IvyPublicationCoordinates coordinates,
        String extension,
        String type,
        @Nullable String classifier,
        TaskDependencyFactory taskDependencyFactory,
        ProviderFactory providerFactory,
        ObjectFactory objectFactory
    ) {
        super(taskDependencyFactory, providerFactory, objectFactory);
        this.generator = generator;
        this.coordinates = coordinates;
        this.defaultExtension = extension;
        this.defaultType = type;
        this.defaultClassifier = classifier;
        this.buildDependencies = taskDependencyFactory.visitingDependencies(context -> {
            context.add(generator.get());
        });
    }

    @Override
    protected Provider<String> getDefaultName() {
        return coordinates.getModule();
    }

    @Override
    protected Provider<String> getDefaultType() {
        return Providers.of(defaultType);
    }

    @Override
    protected Provider<String> getDefaultExtension() {
        return Providers.of(defaultExtension);
    }

    @Override
    protected Provider<String> getDefaultClassifier() {
        return Providers.ofNullable(defaultClassifier);
    }

    @Override
    protected Provider<String> getDefaultConf() {
        return Providers.notDefined();
    }

    @Override
    protected TaskDependency getDefaultBuildDependencies() {
        return buildDependencies;
    }

    @Override
    public Provider<RegularFile> getFile() {
        return Providers.of(() -> generator.get().getOutputs().getFiles().getSingleFile());
    }

    public boolean isEnabled() {
        TaskInternal task = (TaskInternal) generator.get();
        return task.getOnlyIf().isSatisfiedBy(task);
    }

    @Override
    public boolean shouldBePublished() {
        return isEnabled();
    }
}
