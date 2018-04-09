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

package org.gradle.api.internal.artifacts.configurations;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactBackedResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformTask;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformationResult;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformer;
import org.gradle.api.internal.artifacts.transform.ChainedTransformer;
import org.gradle.api.internal.artifacts.transform.InitialArtifactTransformTask;
import org.gradle.api.internal.artifacts.transform.IntermediateArtifactTransformTask;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.util.NameValidator;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@NonNullApi
public class ArtifactTransformTaskRegistry {

    private final ConcurrentHashMap<ArtifactTransformTaskKey, ArtifactTransformTask> results = new ConcurrentHashMap<ArtifactTransformTaskKey, ArtifactTransformTask>();
    private final ITaskFactory taskFactory;

    public ArtifactTransformTaskRegistry(ITaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    @Nullable
    public Map<ComponentArtifactIdentifier, ArtifactTransformationResult> get(ResolvedArtifactSet artifactSet, ArtifactTransformer transformer) {
        List<ArtifactTransformer> artifactTransformers = unpackTransformer(transformer);
        List<ResolvedArtifactSet> artifactSets = CompositeResolvedArtifactSet.unpack(artifactSet);
        ImmutableMap.Builder<ComponentArtifactIdentifier, ArtifactTransformationResult> builder = ImmutableMap.builder();
        for (ResolvedArtifactSet set : artifactSets) {
            if (!(set instanceof ArtifactBackedResolvedVariant.SingleArtifactSet)) {
                return null;
            }
            ComponentArtifactIdentifier identifier = getIdentifier((ArtifactBackedResolvedVariant.SingleArtifactSet) set);
            final ArtifactTransformTask artifactTransformTask = results.get(new ArtifactTransformTaskKey(identifier, artifactTransformers));
            if (artifactTransformTask == null) {
                return null;
            }
            builder.put(identifier, new ArtifactTransformationResult() {
                @Override
                public Throwable getFailure() {
                    return artifactTransformTask.getTransformationResult().getFailure();
                }

                @Override
                public List<File> getResult() {
                    return artifactTransformTask.getTransformationResult().getResult();
                }

                @Override
                public boolean isFailed() {
                    return artifactTransformTask.getTransformationResult().isFailed();
                }
            });
        }
        return builder.build();
    }

    public ArtifactTransformTask getOrCreate(ArtifactBackedResolvedVariant.SingleArtifactSet delegate, ArtifactTransformer transformer) {
        return getOrCreate(unpackTransformer(transformer), delegate, getIdentifier(delegate));
    }

    private ArtifactTransformTask getOrCreate(List<ArtifactTransformer> transformers, ArtifactBackedResolvedVariant.SingleArtifactSet artifactSet, ComponentArtifactIdentifier artifactIdentifier) {
        ArtifactTransformTaskKey key = new ArtifactTransformTaskKey(artifactIdentifier, transformers);
        if (results.containsKey(key)) {
            return results.get(key);
        }

        ArtifactTransformer transformer = transformers.get(transformers.size() - 1);
        String taskName = taskNameFor(transformer, artifactSet);
        if (transformers.size() == 1) {
            InitialArtifactTransformTask initialArtifactTransformTask = taskFactory.create(
                taskName,
                InitialArtifactTransformTask.class,
                transformer,
                artifactSet
            );
            results.put(key, initialArtifactTransformTask);
            return initialArtifactTransformTask;
        }

        ArtifactTransformTask nextTransformerTask = getOrCreate(transformers.subList(0, transformers.size() - 1), artifactSet, artifactIdentifier);
        IntermediateArtifactTransformTask intermediateArtifactTransformTask = taskFactory.create(
            taskName,
            IntermediateArtifactTransformTask.class,
            transformer,
            nextTransformerTask
        );
        results.put(key, intermediateArtifactTransformTask);
        return intermediateArtifactTransformTask;
    }

    private List<ArtifactTransformer> unpackTransformer(ArtifactTransformer transformer) {
        if (transformer instanceof ChainedTransformer) {
            ChainedTransformer chainedTransformer = (ChainedTransformer) transformer;
            return ImmutableList.<ArtifactTransformer>builder().addAll(unpackTransformer(chainedTransformer.getFirst())).addAll(unpackTransformer(chainedTransformer.getSecond())).build();
        }
        return ImmutableList.of(transformer);
    }

    private String taskNameFor(ArtifactTransformer transformer, ArtifactBackedResolvedVariant.SingleArtifactSet artifactSet) {
        return NameValidator.asReallyValidName(Joiner.on("-").join(transformer.getDisplayName(), artifactSet.getArtifact().getId().getDisplayName(), System.nanoTime()));
    }

    private static ComponentArtifactIdentifier getIdentifier(ArtifactBackedResolvedVariant.SingleArtifactSet artifactSet) {
        return artifactSet.getArtifact().getId();
    }

    private static class ArtifactTransformTaskKey {
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final List<ArtifactTransformer> transformers;

        private ArtifactTransformTaskKey(ComponentArtifactIdentifier artifactIdentifier, List<ArtifactTransformer> transformers) {
            this.artifactIdentifier = artifactIdentifier;
            this.transformers = transformers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ArtifactTransformTaskKey that = (ArtifactTransformTaskKey) o;

            if (!artifactIdentifier.equals(that.artifactIdentifier)) {
                return false;
            }
            return transformers.equals(that.transformers);
        }

        @Override
        public int hashCode() {
            int result = artifactIdentifier.hashCode();
            result = 31 * result + transformers.hashCode();
            return result;
        }
    }
}
