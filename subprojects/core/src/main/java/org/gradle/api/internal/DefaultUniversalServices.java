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

package org.gradle.api.internal;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;

public abstract class DefaultUniversalServices implements Project.AllTimeServices, Task.AllTimeServices {
    private final ArchiveOperations archiveOperations;
    private final ObjectFactory objectFactory;
    private final ProjectLayout projectLayout;
    private final ProviderFactory providerFactory;
    private final FileSystemOperations fileSystemOperations;

    public DefaultUniversalServices(
        ArchiveOperations archiveOperations,
        ObjectFactory objectFactory,
        ProjectLayout projectLayout,
        ProviderFactory providerFactory,
        FileSystemOperations fileSystemOperations
    ) {
        this.archiveOperations = archiveOperations;
        this.objectFactory = objectFactory;
        this.projectLayout = projectLayout;
        this.providerFactory = providerFactory;
        this.fileSystemOperations = fileSystemOperations;
    }

    @Override
    public ArchiveOperations getArchiveOperations() {
        return archiveOperations;
    }

    @Override
    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    @Override
    public ProjectLayout getProjectLayout() {
        return projectLayout;
    }

    @Override
    public ProviderFactory getProviderFactory() {
        return providerFactory;
    }

    @Override
    public FileSystemOperations getFileSystemOperations() {
        return fileSystemOperations;
    }
}
