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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

public class SingleOutputTaskIvyArtifact extends AbstractIvyArtifact {

    private final TaskProvider<? extends Task> generator;
    private final IvyPublicationIdentity identity;
    private final String extension;
    private final String type;
    private final String classifier;
    private final TaskDependencyInternal buildDependencies;

    public SingleOutputTaskIvyArtifact(TaskProvider<? extends Task> generator, IvyPublicationIdentity identity, String extension, String type, String classifier) {
        this.generator = generator;
        this.identity = identity;
        this.extension = extension;
        this.type = type;
        this.classifier = classifier;
        this.buildDependencies = new GeneratorTaskDependency();
    }

    @Override
    protected String getDefaultName() {
        return identity.getModule();
    }

    @Override
    protected String getDefaultType() {
        return type;
    }

    @Override
    protected String getDefaultExtension() {
        return extension;
    }

    @Override
    protected String getDefaultClassifier() {
        return classifier;
    }

    @Override
    protected String getDefaultConf() {
        return null;
    }

    @Override
    protected TaskDependencyInternal getDefaultBuildDependencies() {
        return buildDependencies;
    }

    @Override
    public File getFile() {
        return generator.get().getOutputs().getFiles().getSingleFile();
    }

    public boolean isEnabled() {
        TaskInternal task = (TaskInternal) generator.get();
        return task.getOnlyIf().isSatisfiedBy(task);
    }

    private class GeneratorTaskDependency extends AbstractTaskDependency {
        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(generator.get());
        }
    }

    @Override
    public boolean shouldBePublished() {
        return isEnabled();
    }
}
