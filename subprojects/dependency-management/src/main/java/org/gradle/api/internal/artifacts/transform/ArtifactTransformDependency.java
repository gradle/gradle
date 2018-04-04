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

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.artifacts.configurations.ArtifactTransformTaskRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

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

        List<ArtifactTransformResult> results = new ArrayList<ArtifactTransformResult>();
        if (delegate instanceof CompositeResolvedArtifactSet) {
            for (ResolvedArtifactSet artifactSet : ((CompositeResolvedArtifactSet) delegate).getSets()) {
                results.add(createTransformTasks(artifactSet, transform, artifactTransformTaskRegistry, context));
            }
        } else {
            results.add(createTransformTasks(delegate, transform, artifactTransformTaskRegistry, context));
        }
        artifactTransformTaskRegistry.register(delegate, transform, CompositeArtifactTransformResult.create(results));
    }

    private ArtifactTransformResult createTransformTasks(ResolvedArtifactSet resolvedArtifactSet, ArtifactTransformer transform, ArtifactTransformTaskRegistry artifactTransformTaskRegistry, TaskDependencyResolveContext context) {
        ArtifactTransformResult transformTask = createAndUnpackTransformTask(transform, resolvedArtifactSet, null, artifactTransformTaskRegistry);
        artifactTransformTaskRegistry.register(resolvedArtifactSet, transform, transformTask);
        context.add(transformTask);
        return transformTask;
    }

    private ArtifactTransformTask createAndUnpackTransformTask(ArtifactTransformer transform, ResolvedArtifactSet delegate, @Nullable ArtifactTransformTask innerTransformTask, ArtifactTransformTaskRegistry artifactTransformTaskRegistry) {
        if (transform instanceof ChainedTransformer) {
            ChainedTransformer transformer = (ChainedTransformer) transform;
            ArtifactTransformTask inner = createAndUnpackTransformTask(transformer.getFirst(), delegate, innerTransformTask, artifactTransformTaskRegistry);
            return createAndUnpackTransformTask(transformer.getSecond(), delegate, inner, artifactTransformTaskRegistry);
        }
        return artifactTransformTaskRegistry.getOrCreate(delegate, transform, innerTransformTask);
    }
}
