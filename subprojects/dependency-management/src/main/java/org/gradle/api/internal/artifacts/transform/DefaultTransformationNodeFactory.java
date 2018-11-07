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
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildableSingleResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DefaultTransformationNodeFactory implements TransformationNodeFactory {
    private final Map<ArtifactTransformKey, TransformationNode> transformations = Maps.newConcurrentMap();

    @Override
    public Collection<TransformationNode> getOrCreate(ResolvedArtifactSet artifactSet, Transformation transformation, ConfigurationInternal configuration) {
        final List<TransformationStep> transformationChain = unpackTransformation(transformation);
        final ImmutableList.Builder<TransformationNode> builder = ImmutableList.builder();
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
                TransformationNode transformationNode = getOrCreate(singleArtifactSet, transformationChain, configuration);
                builder.add(transformationNode);
                return true;
            }
        });
        return builder.build();
    }

    private TransformationNode getOrCreate(BuildableSingleResolvedArtifactSet singleArtifactSet, List<TransformationStep> transformationChain, ConfigurationInternal configuration) {
        ArtifactTransformKey key = new ArtifactTransformKey(singleArtifactSet.getArtifactId(), transformationChain);
        TransformationNode transformationNode = transformations.get(key);
        if (transformationNode == null) {
            if (transformationChain.size() == 1) {
                transformationNode = TransformationNode.initial(transformationChain.iterator().next(), singleArtifactSet, configuration);
            } else {
                TransformationNode previous = getOrCreate(singleArtifactSet, transformationChain.subList(0, transformationChain.size() - 1), configuration);
                transformationNode = TransformationNode.chained(transformationChain.get(transformationChain.size() - 1), previous);
            }
            transformations.put(key, transformationNode);
        }
        return transformationNode;
    }

    static List<TransformationStep> unpackTransformation(Transformation transformation) {
        final ImmutableList.Builder<TransformationStep> builder = ImmutableList.builder();
        transformation.visitTransformationSteps(new Action<TransformationStep>() {
            @Override
            public void execute(TransformationStep transformation) {
                builder.add(transformation);
            }
        });
        return builder.build();
    }

    private static class ArtifactTransformKey {
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final List<TransformationStep> transformations;

        private ArtifactTransformKey(ComponentArtifactIdentifier artifactIdentifier, List<TransformationStep> transformations) {
            this.artifactIdentifier = artifactIdentifier;
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

            if (!artifactIdentifier.equals(that.artifactIdentifier)) {
                return false;
            }
            return transformations.equals(that.transformations);
        }

        @Override
        public int hashCode() {
            int result = artifactIdentifier.hashCode();
            result = 31 * result + transformations.hashCode();
            return result;
        }
    }
}
