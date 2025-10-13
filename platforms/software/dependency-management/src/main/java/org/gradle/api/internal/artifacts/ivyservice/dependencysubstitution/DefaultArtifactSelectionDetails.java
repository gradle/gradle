/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.DependencyArtifactSelector;
import org.gradle.internal.component.model.IvyArtifactName;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultArtifactSelectionDetails implements ArtifactSelectionDetailsInternal {

    private final ImmutableList<IvyArtifactName> requestedArtifacts;
    private @Nullable List<DependencyArtifactSelector> targetSelectors;

    public DefaultArtifactSelectionDetails(ImmutableList<IvyArtifactName> requestedArtifacts) {
        this.requestedArtifacts = requestedArtifacts;
    }

    @Override
    public boolean hasSelectors() {
        return !requestedArtifacts.isEmpty();
    }

    @Override
    public List<DependencyArtifactSelector> getRequestedSelectors() {
        return Lists.transform(requestedArtifacts, artifact ->
            new DefaultDependencyArtifactSelector(artifact.getType(), artifact.getExtension(), artifact.getClassifier())
        );
    }

    @Override
    public void withoutArtifactSelectors() {
        targetSelectors = new ArrayList<>();
    }

    @Override
    public void selectArtifact(String type, @Nullable String extension, @Nullable String classifier) {
        selectArtifact(new DefaultDependencyArtifactSelector(type, extension, classifier));
    }

    @Override
    public void selectArtifact(DependencyArtifactSelector selector) {
        if (targetSelectors == null) {
            targetSelectors = new ArrayList<>();
        }
        targetSelectors.add(selector);
    }

    @Override
    public @Nullable ImmutableList<DependencyArtifactSelector> getConfiguredSelectors() {
        return targetSelectors != null ? ImmutableList.copyOf(targetSelectors) : null;
    }

}
