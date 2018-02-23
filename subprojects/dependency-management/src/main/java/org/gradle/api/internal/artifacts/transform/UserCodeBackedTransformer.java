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

import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformException;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.changedetection.state.isolation.Isolatable;
import org.gradle.api.internal.changedetection.state.isolation.IsolatableFactory;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.type.ModelType;

import java.io.File;
import java.util.Arrays;
import java.util.List;

class UserCodeBackedTransformer implements VariantTransformRegistry.Registration, ArtifactTransformer {
    private final ImmutableAttributes from;
    private final ImmutableAttributes to;
    private final HashCode inputsHash;
    private final TransformedFileCache transformedFileCache;
    private final TransformArtifactsAction transformer;

    public static UserCodeBackedTransformer create(ImmutableAttributes from, ImmutableAttributes to, Class<? extends ArtifactTransform> implementation, Object[] params, TransformedFileCache transformedFileCache, IsolatableFactory isolatableFactory, ClassLoaderHierarchyHasher classLoaderHierarchyHasher, Instantiator instantiator) {
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        hasher.putString(implementation.getName());
        hasher.putHash(classLoaderHierarchyHasher.getClassLoaderHash(implementation.getClassLoader()));

        // TODO - should snapshot later?
        Isolatable<Object[]> paramsSnapshot;
        try {
            paramsSnapshot = isolatableFactory.isolate(params);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(String.format("Could not snapshot configuration values for transform %s: %s", ModelType.of(implementation).getDisplayName(), Arrays.asList(params)), e);
        }

        paramsSnapshot.appendToHasher(hasher);

        TransformArtifactsAction transformer = new TransformArtifactsAction(implementation, paramsSnapshot, instantiator);
        return new UserCodeBackedTransformer(from, to, transformer, hasher.hash(), transformedFileCache);
    }

    private UserCodeBackedTransformer(ImmutableAttributes from, ImmutableAttributes to, TransformArtifactsAction transformer, HashCode inputHash, TransformedFileCache cache) {
        this.from = from;
        this.to = to;
        this.transformer = transformer;
        this.inputsHash = inputHash;
        this.transformedFileCache = cache;
    }

    public AttributeContainerInternal getFrom() {
        return from;
    }

    public AttributeContainerInternal getTo() {
        return to;
    }

    public ArtifactTransformer getArtifactTransform() {
        return this;
    }

    @Override
    public List<File> transform(File input) {
        try {
            File absoluteFile = input.getAbsoluteFile();
            return transformedFileCache.getResult(absoluteFile, inputsHash, transformer);
        } catch (Throwable t) {
            throw new ArtifactTransformException(input, to, transformer.getImplementationClass(), t);
        }
    }

    @Override
    public boolean hasCachedResult(File input) {
        return transformedFileCache.contains(input.getAbsoluteFile(), inputsHash);
    }

    @Override
    public String getDisplayName() {
        return transformer.getDisplayName();
    }
}
