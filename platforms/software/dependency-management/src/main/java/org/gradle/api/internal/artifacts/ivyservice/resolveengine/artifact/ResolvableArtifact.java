/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.tasks.AbstractTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyUtil;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.model.CalculatedValue;

import java.io.File;

/**
 * Represents an artifact that can be resolved. Call {@link #getFile()} or {@link ResolvedArtifact#getFile()} to resolve.
 */
public interface ResolvableArtifact extends TaskDependencyContainer {
    ComponentArtifactIdentifier getId();

    /**
     * Should this artifact be resolved synchronously? For example, is the result of {@link #getFile()} available in memory or can it be calculated quickly without IO calls?
     */
    boolean isResolveSynchronously();

    IvyArtifactName getArtifactName();

    /**
     * Resolves the file, if not already, blocking until complete.
     */
    File getFile();

    /**
     * Returns the artifact file as a lazy type. Does not resolve the file, but the returned value can be used to do so.
     */
    CalculatedValue<File> getFileSource();

    ResolvableArtifact transformedTo(File file);

    ResolvedArtifact toPublicView();

    default void visitProducerTasks(Action<? super Task> visitor) {
        visitDependencies(new AbstractTaskDependencyResolveContext() {
            @Override
            public void add(Object dependency) {
                if (dependency instanceof Task) {
                    visitor.execute((Task) dependency);
                } else if (dependency instanceof TaskDependency) {
                    TaskDependencyUtil.getDependenciesForInternalUse((TaskDependency) dependency, null).forEach(visitor::execute);
                } else if (dependency instanceof TaskDependencyContainer) {
                    ((TaskDependencyContainer) dependency).visitDependencies(this);
                } else if (dependency instanceof Buildable) {
                    TaskDependencyUtil.getDependenciesForInternalUse((Buildable) dependency).forEach(visitor::execute);
                } else if (dependency instanceof WorkNodeAction) {
                    ((WorkNodeAction) dependency).visitDependencies(this);
                } else {
                    throw new AssertionError(String.format("Unexpected dependency type: %s", dependency.getClass().getName()));
                }
            }
        });
    }
}
