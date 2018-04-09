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

import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.publish.PublicationArtifact;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import java.io.File;

public class SimplePublicationArtifact implements PublicationArtifact {

    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
    private final FileCollection files;

    public SimplePublicationArtifact(FileCollection files) {
        this.files = files;
    }

    @Override
    public File getFile() {
        return files.getSingleFile();
    }

    public FileCollection getFiles() {
        return files;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    @Override
    public void builtBy(Object... tasks) {
        buildDependencies.add(tasks);
    }

    @Nullable
    @Override
    public String getClassifier() {
        return null;
    }

    @Override
    public void setClassifier(@Nullable String classifier) {
    }
}
