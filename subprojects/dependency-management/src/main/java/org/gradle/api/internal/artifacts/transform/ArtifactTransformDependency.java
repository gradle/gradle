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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.Iterables;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.artifacts.configurations.ArtifactTransformTaskRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactBackedResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

@NonNullApi
public class ArtifactTransformDependency implements TaskDependencyContainer {
    private final ArtifactTransformer transform;
    private final ResolvedArtifactSet delegate;

    public ArtifactTransformDependency(ArtifactTransformer transform, ResolvedArtifactSet delegate) {
        this.transform = transform;
        this.delegate = delegate;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        ArtifactTransformTaskRegistry artifactTransformTaskRegistry = ((ProjectInternal) context.getTask().getProject()).getServices().get(ArtifactTransformTaskRegistry.class);

        Iterable<ArtifactBackedResolvedVariant.SingleArtifactSet> singleArtifactSets = unpackResolvedArtifactSet(delegate);
        for (ArtifactBackedResolvedVariant.SingleArtifactSet singleArtifactSet : singleArtifactSets) {
            ArtifactTransformTask transformTask = artifactTransformTaskRegistry.getOrCreate(singleArtifactSet, transform);
            context.add(transformTask);
        }
    }

    private Iterable<ArtifactBackedResolvedVariant.SingleArtifactSet> unpackResolvedArtifactSet(ResolvedArtifactSet initialArtifactSet) {
        return Iterables.filter(CompositeResolvedArtifactSet.unpack(initialArtifactSet), ArtifactBackedResolvedVariant.SingleArtifactSet.class);
    }
}
