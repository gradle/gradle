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

package org.gradle.api.publish.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublicationArtifact;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import java.io.File;

public class TaskOutputPublicationArtifact implements PublicationArtifact {

    private final DefaultTaskDependency buildDependencies;
    private final Task task;
    private final Provider<String> name;
    private final String extension;
    private final String type;

    public TaskOutputPublicationArtifact(Task task, String extension) {
        this(task, Providers.<String>notDefined(), extension, extension);
    }

    public TaskOutputPublicationArtifact(Task task, Provider<String> name, String extension, String type) {
        this.task = task;
        this.buildDependencies = new DefaultTaskDependency(null, ImmutableSet.of((Object) task));
        this.name = name;
        this.extension = extension;
        this.type = type;
    }

    @Override
    public File getFile() {
        return task.getOutputs().getFiles().getSingleFile();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    @Override
    public String getName() {
        return name.getOrElse("");
    }

    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public String getType() {
        return type;
    }

    @Nullable
    @Override
    public String getClassifier() {
        return null;
    }
}
