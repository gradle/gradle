/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;

public class NormalizedIvyArtifact implements IvyArtifactInternal, Serializable {
    private final File file;
    private final String extension;
    @Nullable
    private final String classifier;
    private final String name;
    private final String type;
    @Nullable
    private final String conf;
    private final Provider<Boolean> shouldBePublished;

    public NormalizedIvyArtifact(IvyArtifactInternal artifact) {
        this.name = artifact.getName();
        this.type = artifact.getType();
        this.conf = artifact.getConf();
        this.file = artifact.getFile();
        this.extension = artifact.getExtension();
        this.classifier = artifact.getClassifier();
        this.shouldBePublished = new DefaultProvider<>(artifact::shouldBePublished);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        throw new IllegalStateException();
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void setType(String type) {
        throw new IllegalStateException();
    }

    @Nullable
    @Override
    public String getConf() {
        return conf;
    }

    @Override
    public void setConf(@Nullable String conf) {
        throw new IllegalStateException();
    }

    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public void setExtension(String extension) {
        throw new IllegalStateException();
    }

    @Nullable
    @Override
    public String getClassifier() {
        return classifier;
    }

    @Override
    public void setClassifier(@Nullable String classifier) {
        throw new IllegalStateException();
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public void builtBy(Object... tasks) {
        throw new IllegalStateException();
    }

    @Nonnull
    @Override
    public TaskDependency getBuildDependencies() {
        throw new IllegalStateException();
    }

    @Override
    public boolean shouldBePublished() {
        return shouldBePublished.get();
    }

    @Override
    public NormalizedIvyArtifact asNormalisedArtifact() {
        return this;
    }
}
