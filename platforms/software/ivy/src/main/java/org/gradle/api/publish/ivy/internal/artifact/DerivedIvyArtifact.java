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

import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.tasks.TaskDependency;

import static com.google.common.io.Files.getFileExtension;

public class DerivedIvyArtifact extends AbstractIvyArtifact {
    private final IvyArtifact original;
    private final PublicationInternal.DerivedArtifact derived;

    public DerivedIvyArtifact(
        IvyArtifact original,
        PublicationInternal.DerivedArtifact derived,
        TaskDependencyFactory taskDependencyFactory,
        ProviderFactory providerFactory,
        ObjectFactory objectFactory
    ) {
        super(taskDependencyFactory, providerFactory, objectFactory);
        this.original = original;
        this.derived = derived;
    }

    @Override
    protected Provider<String> getDefaultName() {
        return original.getName();
    }

    @Override
    protected Provider<String> getDefaultType() {
        return getFile().map(f -> getFileExtension(f.getAsFile().getName()));
    }

    @Override
    protected Provider<String> getDefaultExtension() {
        return original.getExtension().map(old -> old + "." + getType().get());
    }

    @Override
    protected Provider<String> getDefaultClassifier() {
        return original.getClassifier();
    }

    @Override
    protected Provider<String> getDefaultConf() {
        return original.getConf();
    }

    @Override
    protected TaskDependency getDefaultBuildDependencies() {
        return original.getBuildDependencies();
    }

    @Override
    public Provider<RegularFile> getFile() {
        return Providers.of(derived::create);
    }

    @Override
    public boolean shouldBePublished() {
        return derived.shouldBePublished();
    }
}
