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

package org.gradle.api.publish.maven.internal.artifact;

import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;

public class SingleOutputTaskMavenArtifact extends AbstractMavenArtifact {
    private final TaskProvider<? extends Task> generator;
    private final String extension;
    private final String classifier;
    private final TaskDependencyInternal buildDependencies;

    public SingleOutputTaskMavenArtifact(
        TaskProvider<? extends Task> generator,
        String extension,
        String classifier,
        TaskDependencyFactory taskDependencyFactory,
        ObjectFactory objectFactory,
        ProviderFactory providerFactory
    ) {
        super(taskDependencyFactory, objectFactory, providerFactory);
        this.generator = generator;
        this.extension = extension;
        this.classifier = classifier;
        this.buildDependencies = taskDependencyFactory.visitingDependencies(context -> context.add(getGenerator()));
    }

    @Override
    public Provider<RegularFile> getFile() {
        return Providers.of(() -> getGenerator().getOutputs().getFiles().getSingleFile());
    }

    private Task getGenerator() {
        return generator.get();
    }

    @Override
    protected Provider<String> getDefaultExtension() {
        return Providers.of(extension);
    }

    @Override
    protected Provider<String> getDefaultClassifier() {
        return Providers.ofNullable(classifier);
    }

    @Override
    protected TaskDependencyInternal getDefaultBuildDependencies() {
        return buildDependencies;
    }

    public boolean isEnabled() {
        return getGenerator().getEnabled();
    }

    @Override
    public boolean shouldBePublished() {
        return isEnabled();
    }
}
