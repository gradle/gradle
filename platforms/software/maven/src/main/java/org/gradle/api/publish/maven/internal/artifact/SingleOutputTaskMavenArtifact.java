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

import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.provider.Provider;

import java.io.File;

public class SingleOutputTaskMavenArtifact extends AbstractMavenArtifact {
    private final Provider<RegularFile> file;
    private final Provider<Boolean> enabled;
    private final String extension;
    private final String classifier;
    private final TaskDependencyInternal buildDependencies;

    public SingleOutputTaskMavenArtifact(
        Provider<RegularFile> file,
        Provider<Boolean> enabled,
        String extension,
        String classifier,
        TaskDependencyFactory taskDependencyFactory
    ) {
        super(taskDependencyFactory);
        this.file = file;
        this.enabled = enabled;
        this.extension = extension;
        this.classifier = classifier;
        this.buildDependencies = taskDependencyFactory.visitingDependencies(context -> context.add(file));
    }

    @Override
    public File getFile() {
        return file.get().getAsFile();
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
        return enabled.get();
    }

    @Override
    public boolean shouldBePublished() {
        return isEnabled();
    }
}
