/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.util.List;
import java.util.Set;

public interface LocalConfigurationMetadata extends ConfigurationMetadata {

    String getDescription();

    Set<String> getExtendsFrom();

    @Override
    List<? extends LocalOriginDependencyMetadata> getDependencies();

    @Override
    Set<? extends LocalComponentArtifactMetadata> getArtifacts();

    /**
     * Returns the files attached to this configuration, if any. These should be represented as dependencies, but are currently represented as files as a migration step.
     */
    Set<LocalFileDependencyMetadata> getFiles();
}
