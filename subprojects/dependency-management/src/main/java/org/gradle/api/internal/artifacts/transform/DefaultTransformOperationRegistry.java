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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildableSingleResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.BuildOperationExecutor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class DefaultTransformOperationRegistry implements TransformOperationRegistry {
    private final Map<ArtifactTransformOperationKey, TransformInfo> transformations = Maps.newConcurrentMap();
    private final BuildOperationExecutor buildOperationExecutor;

    public DefaultTransformOperationRegistry(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public Map<ComponentArtifactIdentifier, TransformInfo> getOrCreateResults(ResolvedArtifactSet artifactSet, ArtifactTransformer transformer) {
        Map<ComponentArtifactIdentifier, TransformInfo> results = getOrCreate(artifactSet, transformer, new TransformInfoFactory() {
            @Override
            public TransformInfo create(List<UserCodeBackedTransformer> transformerChain, BuildableSingleResolvedArtifactSet artifactSet) {
                return TransformInfo.from(transformerChain, artifactSet, buildOperationExecutor);
            }
        });
        assert results != null;
        return results;
    }

    @Override
    @Nullable
    public Map<ComponentArtifactIdentifier, TransformOperation> getResults(ResolvedArtifactSet artifactSet, final ArtifactTransformer transformer) {
        return Cast.uncheckedCast(getOrCreate(artifactSet, transformer, null));
    }

    @Nullable
    private Map<ComponentArtifactIdentifier, TransformInfo> getOrCreate(final ResolvedArtifactSet artifactSet, ArtifactTransformer transformer, @Nullable final TransformInfoFactory factory) {
        final List<UserCodeBackedTransformer> transformerChain = unpackTransformerChain(transformer);
        final ImmutableMap.Builder<ComponentArtifactIdentifier, TransformInfo> builder = ImmutableMap.builder();
        boolean collected = CompositeResolvedArtifactSet.visitHierarchy(artifactSet, new CompositeResolvedArtifactSet.ResolvedArtifactSetVisitor() {
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
                ArtifactTransformOperationKey key = new ArtifactTransformOperationKey(singleArtifactSet.getArtifactId(), transformerChain);
                TransformInfo transformInfo = transformations.get(key);
                if (transformInfo == null) {
                    if (factory == null) {
                        return false;
                    }
                    transformInfo = factory.create(transformerChain, singleArtifactSet);
                    transformations.put(key, transformInfo);
                }
                builder.put(singleArtifactSet.getArtifactId(), transformInfo);
                return true;
            }
        });
        return collected ? builder.build() : null;
    }

    private interface TransformInfoFactory {
        TransformInfo create(List<UserCodeBackedTransformer> transformerChain, BuildableSingleResolvedArtifactSet artifactSet);
    }

    private static List<UserCodeBackedTransformer> unpackTransformerChain(ArtifactTransformer transformer) {
        final List<UserCodeBackedTransformer> transformerChain = Lists.newArrayList();
        transformer.visitLeafTransformers(new Action<ArtifactTransformer>() {
            @Override
            public void execute(ArtifactTransformer transformer) {
                transformerChain.add((UserCodeBackedTransformer) transformer);
            }
        });
        return transformerChain;
    }

    private static class ArtifactTransformOperationKey {
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final List<UserCodeBackedTransformer> transformers;

        private ArtifactTransformOperationKey(ComponentArtifactIdentifier artifactIdentifier, List<UserCodeBackedTransformer> transformers) {
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

            ArtifactTransformOperationKey that = (ArtifactTransformOperationKey) o;

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
