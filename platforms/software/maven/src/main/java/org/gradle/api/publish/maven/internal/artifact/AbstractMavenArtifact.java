/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.internal.PublicationArtifactInternal;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.tasks.TaskDependency;

public abstract class AbstractMavenArtifact implements MavenArtifact, PublicationArtifactInternal {
    private final TaskDependency allBuildDependencies;
    private final DefaultTaskDependency additionalBuildDependencies;
    private final Property<String> extensionProperty;
    private final Property<String> classifierProperty;

    protected AbstractMavenArtifact(
        TaskDependencyFactory taskDependencyFactory,
        ObjectFactory objectFactory,
        ProviderFactory providerFactory
    ) {
        this.additionalBuildDependencies = new DefaultTaskDependency();
        this.allBuildDependencies = taskDependencyFactory.visitingDependencies(context -> {
            context.add(getDefaultBuildDependencies());
            additionalBuildDependencies.visitDependencies(context);
        });
        this.extensionProperty = objectFactory.property(String.class);
        this.classifierProperty = objectFactory.property(String.class);
        // those should be lazy because the fields are not yet initialized in child classes
        getExtension().set(providerFactory.provider(() -> getDefaultExtension().getOrNull()));
        getClassifier().set(providerFactory.provider(() -> getDefaultClassifier().getOrNull()));
    }

    @Override
    public Property<String> getExtension() {
        return extensionProperty;
    }

    protected abstract Provider<String> getDefaultExtension();

    @Override
    public Property<String> getClassifier() {
        return classifierProperty;
    }

    protected abstract Provider<String> getDefaultClassifier();

    @Override
    public final void builtBy(Object... tasks) {
        additionalBuildDependencies.add(tasks);
    }

    @Override
    public final TaskDependency getBuildDependencies() {
        return allBuildDependencies;
    }

    protected abstract TaskDependency getDefaultBuildDependencies();

    @Override
    public final String toString() {
        return getClass().getSimpleName() + " " + getExtension().getOrNull() + ":" + getClassifier().getOrNull();
    }

}
