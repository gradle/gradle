/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts.publish;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Date;
import java.util.Objects;

/**
 * An immutable implementation of {@link PublishArtifact}.
 *
 * To be used by execution internals only as it doesn't convey task dependencies.
 * It implements equals/hashCode to be used in collections.
 */
public class ImmutablePublishArtifact implements PublishArtifact {
    private final String name;
    private final String extension;
    private final String type;
    private final String classifier;
    private final File file;

    public ImmutablePublishArtifact(String name, String extension, String type, @Nullable String classifier, File file) {
        this.name = name;
        this.extension = extension;
        this.type = type;
        this.classifier = classifier;
        this.file = file;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    @Override
    public String getName() {
        return name;
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
        return classifier;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Nullable
    @Override
    public Date getDate() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ImmutablePublishArtifact that = (ImmutablePublishArtifact) o;
        return name.equals(that.name) && extension.equals(that.extension) && type.equals(that.type) && Objects.equals(classifier, that.classifier) && file.equals(that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, extension, type, classifier, file);
    }
}
