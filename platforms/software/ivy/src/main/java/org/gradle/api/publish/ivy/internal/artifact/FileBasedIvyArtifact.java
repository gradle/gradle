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
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;

public class FileBasedIvyArtifact extends AbstractIvyArtifact {
    private final Provider<RegularFile> file;
    private final IvyPublicationCoordinates coordinates;

    public FileBasedIvyArtifact(
        File file,
        IvyPublicationCoordinates coordinates,
        TaskDependencyFactory taskDependencyFactory,
        ProviderFactory providerFactory,
        ObjectFactory objectFactory
    ) {
        super(taskDependencyFactory, providerFactory, objectFactory);
        this.file = Providers.of(() -> file);
        this.coordinates = coordinates;
    }

    @Override
    protected Provider<String> getDefaultName() {
        return coordinates.getModule();
    }

    @Override
    protected Provider<String> getDefaultType() {
        return getDefaultExtension();
    }

    @Override
    protected Provider<String> getDefaultExtension() {
        return file.map(f -> Files.getFileExtension(f.getAsFile().getName()));
    }

    @Override
    protected Provider<String> getDefaultClassifier() {
        return Providers.notDefined();
    }

    @Override
    protected Provider<String> getDefaultConf() {
        return Providers.notDefined();
    }

    @Override
    protected TaskDependency getDefaultBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    @Override
    public Provider<RegularFile> getFile() {
        return file;
    }

    @Override
    public boolean shouldBePublished() {
        return true;
    }
}
