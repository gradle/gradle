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
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.internal.PublicationInternal;

import static com.google.common.io.Files.getFileExtension;

public class DerivedMavenArtifact extends AbstractMavenArtifact {
    private final AbstractMavenArtifact original;
    private final PublicationInternal.DerivedArtifact derivedFile;

    public DerivedMavenArtifact(
        AbstractMavenArtifact original,
        PublicationInternal.DerivedArtifact derivedFile,
        TaskDependencyFactory taskDependencyFactory,
        ObjectFactory objectFactory,
        ProviderFactory providerFactory
    ) {
        super(taskDependencyFactory, objectFactory, providerFactory);
        this.original = original;
        this.derivedFile = derivedFile;
    }

    @Override
    public Provider<RegularFile> getFile() {
        return Providers.of(derivedFile::create);
    }

    @Override
    protected Provider<String> getDefaultExtension() {
        return original.getExtension().map(old -> old + "." + getFileExtension(getFile().get().getAsFile().getName()));
    }

    @Override
    protected Provider<String> getDefaultClassifier() {
        return original.getClassifier();
    }

    @Override
    protected TaskDependencyInternal getDefaultBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    @Override
    public boolean shouldBePublished() {
        return original.shouldBePublished() && derivedFile.shouldBePublished();
    }
}
