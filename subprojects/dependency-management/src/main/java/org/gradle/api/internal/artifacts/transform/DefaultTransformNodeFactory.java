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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildableSingleResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DefaultTransformNodeFactory implements TransformNodeFactory {
    private final Map<ArtifactTransformKey, TransformNode> transformations = Maps.newConcurrentMap();

    @Override
    public Collection<TransformNode> getOrCreate(ResolvedArtifactSet artifactSet, ArtifactTransformation transformer) {
        final List<ArtifactTransformationStep> transformerChain = unpackTransformerChain(transformer);
        final ImmutableList.Builder<TransformNode> builder = ImmutableList.builder();
        CompositeResolvedArtifactSet.visitHierarchy(artifactSet, new CompositeResolvedArtifactSet.ResolvedArtifactSetVisitor() {
            @Override
            public boolean visitArtifactSet(ResolvedArtifactSet set) {
                if (set instanceof CompositeResolvedArtifactSet) {
                    return true;
                }
                if (!(set instanceof BuildableSingleResolvedArtifactSet)) {
                    throw new IllegalStateException(String.format("Expecting a %s instead of a %s",
                        BuildableSingleResolvedArtifactSet.class.getSimpleName(), set.getClass().getName()));
                }
                BuildableSingleResolvedArtifactSet singleArtifactSet = (BuildableSingleResolvedArtifactSet) set;
                TransformNode transformNode = getOrCreate(singleArtifactSet, transformerChain);
                builder.add(transformNode);
                return true;
            }
        });
        return builder.build();
    }

    private TransformNode getOrCreate(BuildableSingleResolvedArtifactSet singleArtifactSet, List<ArtifactTransformationStep> transformerChain) {
        ArtifactTransformKey key = new ArtifactTransformKey(singleArtifactSet.getArtifactId(), transformerChain);
        TransformNode transformNode = transformations.get(key);
        if (transformNode == null) {
            if (transformerChain.size() == 1) {
                transformNode = TransformNode.initial(transformerChain.iterator().next(), singleArtifactSet);
            } else {
                TransformNode previous = getOrCreate(singleArtifactSet, transformerChain.subList(0, transformerChain.size() - 1));
                transformNode = TransformNode.chained(transformerChain.get(transformerChain.size() - 1), previous, singleArtifactSet.getArtifactId());
            }
            transformations.put(key, transformNode);
        }
        return transformNode;
    }

    private static List<ArtifactTransformationStep> unpackTransformerChain(ArtifactTransformation transformer) {
        final ImmutableList.Builder<ArtifactTransformationStep> builder = ImmutableList.builder();
        transformer.visitTransformationSteps(new Action<ArtifactTransformation>() {
            @Override
            public void execute(ArtifactTransformation transformer) {
                builder.add((ArtifactTransformationStep) transformer);
            }
        });
        return builder.build();
    }

    private static class ArtifactTransformKey {
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final List<ArtifactTransformationStep> transformers;

        private ArtifactTransformKey(ComponentArtifactIdentifier artifactIdentifier, List<ArtifactTransformationStep> transformers) {
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

            ArtifactTransformKey that = (ArtifactTransformKey) o;

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
