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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.hash.HashCode;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformException;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.ValueSnapshot;
import org.gradle.api.internal.changedetection.state.ValueSnapshotter;
import org.gradle.caching.internal.DefaultBuildCacheHasher;

import java.io.File;
import java.util.List;

class DefaultVariantTransformRegistration implements VariantTransformRegistry.Registration {
    private final ImmutableAttributes from;
    private final ImmutableAttributes to;
    private final Transformer<List<File>, File> transform;

    DefaultVariantTransformRegistration(AttributeContainerInternal from, AttributeContainerInternal to, Class<? extends ArtifactTransform> implementation, Object[] params, TransformedFileCache transformedFileCache, ArtifactCacheMetaData artifactCacheMetaData, ValueSnapshotter valueSnapshotter, GenericFileCollectionSnapshotter fileCollectionSnapshotter) {
        this.from = from.asImmutable();
        this.to = to.asImmutable();
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        hasher.putString(implementation.getName());
        // TODO - include implementation hash
        ValueSnapshot snapshot = valueSnapshotter.snapshot(params);
        snapshot.appendToHasher(hasher);
        HashCode inputsHash = hasher.hash();
        this.transform = new ErrorHandlingTransformer(implementation, this.to, transformedFileCache.applyCaching(implementation, params, new ArtifactTransformBackedTransformer(implementation, params, artifactCacheMetaData, inputsHash, fileCollectionSnapshotter)));
    }

    public AttributeContainerInternal getFrom() {
        return from;
    }

    public AttributeContainerInternal getTo() {
        return to;
    }

    public Transformer<List<File>, File> getArtifactTransform() {
        return transform;
    }

    private static class ErrorHandlingTransformer implements Transformer<List<File>, File> {
        private final Class<? extends ArtifactTransform> implementation;
        private final ImmutableAttributes outputAttributes;
        private final Transformer<List<File>, File> transformer;

        ErrorHandlingTransformer(Class<? extends ArtifactTransform> implementation, ImmutableAttributes outputAttributes, Transformer<List<File>, File> transformer) {
            this.implementation = implementation;
            this.outputAttributes = outputAttributes;
            this.transformer = transformer;
        }

        @Override
        public List<File> transform(File input) {
            try {
                return transformer.transform(input);
            } catch (Exception e) {
                throw new ArtifactTransformException(input, outputAttributes, implementation, e);
            }
        }
    }
}
