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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Date;

public class FileSystemPublishArtifact implements PublishArtifactInternal {

    private final FileSystemLocation fileSystemLocation;
    private final String version;
    private ArtifactFile artifactFile;

    public FileSystemPublishArtifact(final FileSystemLocation fileSystemLocation, final String version) {
        this.fileSystemLocation = fileSystemLocation;
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
        return fileSystemLocation.getAsFile();
    }

    @Override
    public Date getDate() {
        return new Date();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    private ArtifactFile getValue() {
        if (artifactFile == null) {
            artifactFile = new ArtifactFile(getFile(), version);
        }
        return artifactFile;
    }

    @Override
    public boolean shouldBePublished() {
        return true;
    }
}
