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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Date;

public class LazyPublishArtifact implements PublishArtifact {
    private final Provider<?> provider;
    private final String version;

    public LazyPublishArtifact(Provider<?> provider, String version) {
        this.provider = provider;
        this.version = version;
    }

    @Override
    public String getName() {
        return getValue().getName();
    }

    @Override
    public String getExtension() {
        return getValue().getExtension();
    }

    @Override
    public String getType() {
        return "";
    }

    @Override
    public String getClassifier() {
        return getValue().getClassifier();
    }

    @Override
    public File getFile() {
        Object value = provider.get();
        if (value instanceof FileSystemLocation) {
            FileSystemLocation location = (FileSystemLocation) value;
            return location.getAsFile();
        }
        if (value instanceof File) {
            return (File) value;
        }
        throw new InvalidUserDataException(String.format("Cannot convert provided value (%s) to a file.", value));
    }

    @Override
    public Date getDate() {
        return new Date();
    }

    private ArtifactFile getValue() {
        return new ArtifactFile(getFile(), version);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                if (provider instanceof TaskDependencyContainer) {
                    context.add(provider);
                }
            }
        };
    }
}
