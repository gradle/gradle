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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.specs.Spec;

import java.util.Map;

public class DefaultVisitedFileDependencyResults implements VisitedFileDependencyResults {
    private final Map<FileCollectionDependency, ArtifactSet> rootFiles;

    public DefaultVisitedFileDependencyResults(Map<FileCollectionDependency, ArtifactSet> rootFiles) {
        this.rootFiles = rootFiles;
    }

    @Override
    public SelectedFileDependencyResults select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector) {
        ImmutableMap.Builder<FileCollectionDependency, ResolvedArtifactSet> rootFilesBuilder = ImmutableMap.builder();
        for (Map.Entry<FileCollectionDependency, ArtifactSet> entry : rootFiles.entrySet()) {
            rootFilesBuilder.put(entry.getKey(), entry.getValue().select(componentFilter, selector));
        }
        return new DefaultFileDependencyResults(rootFilesBuilder.build());
    }

    private static class DefaultFileDependencyResults implements SelectedFileDependencyResults {
        private final Map<FileCollectionDependency, ResolvedArtifactSet> rootFiles;

        DefaultFileDependencyResults(Map<FileCollectionDependency, ResolvedArtifactSet> rootFiles) {
            this.rootFiles = rootFiles;
        }

        @Override
        public Map<FileCollectionDependency, ResolvedArtifactSet> getFirstLevelFiles() {
            return rootFiles;
        }

        @Override
        public ResolvedArtifactSet getArtifactsForNode(long id) {
            return ResolvedArtifactSet.EMPTY;
        }

        @Override
        public ResolvedArtifactSet getArtifactsWithId(long id) {
            return ResolvedArtifactSet.EMPTY;
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return ResolvedArtifactSet.EMPTY;
        }
    }
}
