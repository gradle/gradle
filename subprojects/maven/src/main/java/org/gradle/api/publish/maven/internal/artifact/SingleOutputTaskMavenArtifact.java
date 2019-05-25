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
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

public class SingleOutputTaskMavenArtifact extends AbstractMavenArtifact {
    private final TaskProvider<? extends Task> generator;
    private final String extension;
    private final String classifier;
    private final TaskDependencyInternal buildDependencies;

    public SingleOutputTaskMavenArtifact(TaskProvider<? extends Task> generator, String extension, String classifier) {
        this.generator = generator;
        this.extension = extension;
        this.classifier = classifier;
        this.buildDependencies = new GeneratorTaskDependency();
    }

    @Override
    public File getFile() {
        return generator.get().getOutputs().getFiles().getSingleFile();
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
    protected TaskDependencyInternal getDefaultBuildDependencies() {
        return buildDependencies;
    }

    public boolean isEnabled() {
        return generator.get().getEnabled();
    }

    private class GeneratorTaskDependency extends AbstractTaskDependency {
        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(generator.get());
        }
    }
}
