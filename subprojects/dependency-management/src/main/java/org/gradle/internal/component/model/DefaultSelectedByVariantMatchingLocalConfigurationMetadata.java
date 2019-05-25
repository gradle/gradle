/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.component.model;

import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

import java.util.List;
import java.util.Set;

public class DefaultSelectedByVariantMatchingLocalConfigurationMetadata extends DefaultSelectedByVariantMatchingConfigurationMetadata implements LocalConfigurationMetadata {
    private final LocalConfigurationMetadata delegate;

    DefaultSelectedByVariantMatchingLocalConfigurationMetadata(ConfigurationMetadata delegate) {
        super(delegate);
        this.delegate = (LocalConfigurationMetadata) delegate;
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public Set<String> getExtendsFrom() {
        return delegate.getExtendsFrom();
    }

    @Override
    public Set<LocalFileDependencyMetadata> getFiles() {
        return delegate.getFiles();
    }

    @Override
    public List<? extends LocalOriginDependencyMetadata> getDependencies() {
        return delegate.getDependencies();
    }

    @Override
    public List<? extends LocalComponentArtifactMetadata> getArtifacts() {
        return delegate.getArtifacts();
    }
}
