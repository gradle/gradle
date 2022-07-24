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

import com.google.common.io.Files;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;

public class FileBasedIvyArtifact extends AbstractIvyArtifact {
    private final File file;
    private final String extension;
    private final IvyPublicationIdentity identity;

    public FileBasedIvyArtifact(File file, IvyPublicationIdentity identity) {
        this.file = file;
        extension = Files.getFileExtension(file.getName());
        this.identity = identity;
    }

    @Override
    protected String getDefaultName() {
        return identity.getModule();
    }

    @Override
    protected String getDefaultType() {
        return extension;
    }

    @Override
    protected String getDefaultExtension() {
        return extension;
    }

    @Override
    protected String getDefaultClassifier() {
        return null;
    }

    @Override
    protected String getDefaultConf() {
        return null;
    }

    @Override
    protected TaskDependency getDefaultBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public boolean shouldBePublished() {
        return true;
    }
}
