/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.Buildable;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Collects the file dependencies visited during graph traversal. These should be treated as dependencies, but are currently treated separately as a migration step.
 */
public interface FileDependencyResults {
    /**
     * Returns the direct dependencies of the root node.
     */
    Map<FileCollectionDependency, LocalFileDependencyMetadata> getFirstLevelFiles();

    /**
     * Returns the file dependencies, if any, attached to the given node.
     */
    Set<LocalFileDependencyMetadata> getFiles(ResolvedConfigurationIdentifier node);

    void collectBuildDependencies(Collection<? super Buildable> dest);

    /**
     * Returns all file dependencies seen during traversal.
     */
    Set<LocalFileDependencyMetadata> getFiles();
}
