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

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.internal.operations.BuildOperationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultTransformationNodeRegistry implements TransformationNodeRegistry {
    private final Map<ArtifactTransformKey, TransformationNode> transformations = Maps.newConcurrentMap();
    private final BuildOperationExecutor buildOperationExecutor;
    private final ArtifactTransformListener transformListener;

    public DefaultTransformationNodeRegistry(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.transformListener = transformListener;
    }

    @Override
    public Collection<TransformationNode> getOrCreate(ResolvedArtifactSet artifactSet, Transformation transformation, ExecutionGraphDependenciesResolver dependenciesResolver) {
        final List<Equivalence.Wrapper<TransformationStep>> transformationChain = unpackTransformation(transformation);
        final ImmutableList.Builder<TransformationNode> builder = ImmutableList.builder();
        artifactSet.visitLocalArtifacts(localArtifacts -> {
            TransformationNode transformationNode = getOrCreateInternal(localArtifacts, transformationChain, dependenciesResolver);
            builder.add(transformationNode);
        });
        return builder.build();
    }

    @Override
    public Optional<TransformationNode> getIfExecuted(ComponentArtifactIdentifier artifactId, Transformation transformation) {
        List<Equivalence.Wrapper<TransformationStep>> transformationChain = unpackTransformation(transformation);
        ArtifactTransformKey transformKey = new ArtifactTransformKey(artifactId, transformationChain);
        TransformationNode node = transformations.get(transformKey);
        if (node != null && node.isExecuted()) {
            return Optional.of(node);
        }
        return Optional.empty();
    }

    private TransformationNode getOrCreateInternal(ResolvedArtifactSet.LocalArtifactSet localArtifacts, List<Equivalence.Wrapper<TransformationStep>> transformationChain, ExecutionGraphDependenciesResolver dependenciesResolver) {
        ArtifactTransformKey key = new ArtifactTransformKey(localArtifacts.getId(), transformationChain);
        TransformationNode transformationNode = transformations.get(key);
        if (transformationNode == null) {
            if (transformationChain.size() == 1) {
                transformationNode = TransformationNode.initial(transformationChain.get(0).get(), localArtifacts, dependenciesResolver, buildOperationExecutor, transformListener);
            } else {
                TransformationNode previous = getOrCreateInternal(localArtifacts, transformationChain.subList(0, transformationChain.size() - 1), dependenciesResolver);
                transformationNode = TransformationNode.chained(transformationChain.get(transformationChain.size() - 1).get(), previous, dependenciesResolver, buildOperationExecutor, transformListener);
            }
            transformations.put(key, transformationNode);
        }
        return transformationNode;
    }

    private static List<Equivalence.Wrapper<TransformationStep>> unpackTransformation(Transformation transformation) {
        final ImmutableList.Builder<Equivalence.Wrapper<TransformationStep>> builder = ImmutableList.builder();
        transformation.visitTransformationSteps(transformation1 -> builder.add(TransformationStep.FOR_SCHEDULING.wrap(transformation1)));
        return builder.build();
    }

    private static class ArtifactTransformKey {
        private final Object artifactSetId;
        private final List<Equivalence.Wrapper<TransformationStep>> transformations;

        private ArtifactTransformKey(Object artifactSetId, List<Equivalence.Wrapper<TransformationStep>> transformations) {
            this.artifactSetId = artifactSetId;
            this.transformations = transformations;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ArtifactTransformKey that = (ArtifactTransformKey) o;

            if (!artifactSetId.equals(that.artifactSetId)) {
                return false;
            }
            return transformations.equals(that.transformations);
        }

        @Override
        public int hashCode() {
            int result = artifactSetId.hashCode();
            result = 31 * result + transformations.hashCode();
            return result;
        }
    }
}
