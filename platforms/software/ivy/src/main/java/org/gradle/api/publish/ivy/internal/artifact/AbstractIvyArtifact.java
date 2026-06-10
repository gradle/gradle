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

import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskDependency;
import org.jspecify.annotations.Nullable;

public abstract class AbstractIvyArtifact implements IvyArtifactInternal {
    private final TaskDependency allBuildDependencies;
    private final DefaultTaskDependency additionalBuildDependencies;
    private final Property<String> nameProperty;
    private final Property<String> typeProperty;
    private final Property<String> extensionProperty;
    private final Property<String> classifierProperty;
    private final Property<String> confProperty;

    protected AbstractIvyArtifact(
        TaskDependencyFactory taskDependencyFactory,
        ProviderFactory providerFactory,
        ObjectFactory objectFactory
    ) {
        this.additionalBuildDependencies = new DefaultTaskDependency();
        this.allBuildDependencies = taskDependencyFactory.visitingDependencies(context -> {
            context.add(getDefaultBuildDependencies());
            additionalBuildDependencies.visitDependencies(context);
        });

        this.nameProperty = objectFactory.property(String.class);
        this.typeProperty = objectFactory.property(String.class);
        this.extensionProperty = objectFactory.property(String.class);
        this.classifierProperty = objectFactory.property(String.class);
        this.confProperty = objectFactory.property(String.class);

        // those should be lazy because the fields are not yet initialized in child classes
        getName().set(providerFactory.provider(() -> getDefaultName().getOrNull()));
        getType().set(providerFactory.provider(() -> getDefaultType().getOrNull()));
        getExtension().set(providerFactory.provider(() -> getDefaultExtension().getOrNull()));
        getClassifier().set(providerFactory.provider(() -> getDefaultClassifier().getOrNull()));
        getConf().set(providerFactory.provider(() -> getDefaultConf().getOrNull()));
    }

    @Override
    public Property<String> getName() {
        return nameProperty;
    }

    @Override
    public void setName(String name) {
        getName().set(name);
    }

    protected abstract Provider<String> getDefaultName();

    @Override
    public Property<String> getType() {
        return typeProperty;
    }

    @Override
    public void setType(String type) {
        getType().set(type);
    }

    protected abstract Provider<String> getDefaultType();

    @Override
    public Property<String> getExtension() {
        return extensionProperty;
    }

    @Override
    public void setExtension(String extension) {
        getExtension().set(extension);
    }

    protected abstract Provider<String> getDefaultExtension();

    @Optional
    @Override
    public Property<String> getClassifier() {
        return classifierProperty;
    }

    @Override
    public void setClassifier(@Nullable String classifier) {
        getClassifier().set(classifier);
    }

    protected abstract Provider<String> getDefaultClassifier();

    @Optional
    @Override
    public Property<String> getConf() {
        return confProperty;
    }

    @Override
    public void setConf(@Nullable String conf) {
        getConf().set(conf);
    }

    protected abstract Provider<String> getDefaultConf();

    @Override
    public void builtBy(Object... tasks) {
        additionalBuildDependencies.add(tasks);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return allBuildDependencies;
    }

    protected abstract TaskDependency getDefaultBuildDependencies();

    @Override
    public String toString() {
        return String.format("%s %s:%s:%s:%s", getClass().getSimpleName(), getName().getOrNull(), getType().getOrNull(), getExtension().getOrNull(), getClassifier().getOrNull());
    }
}
