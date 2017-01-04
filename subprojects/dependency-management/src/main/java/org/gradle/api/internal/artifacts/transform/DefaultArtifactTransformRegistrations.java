/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.DefaultAttributeContainer;
import org.gradle.internal.reflect.DirectInstantiator;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultArtifactTransformRegistrations implements ArtifactTransformRegistrationsInternal {
    private final List<ArtifactTransformRegistration> transforms = Lists.newArrayList();

    private final ArtifactAttributeMatcher attributeMatcher;
    private final HashMap<AttributeContainer, AttributeSpecificCache> attributeSpecificCache = Maps.newHashMap();

    private static final Transformer<List<File>, File> NO_TRANSFORM = new Transformer<List<File>, File>() {
        @Override
        public List<File> transform(File file) {
            return null;
        }
    };

    public DefaultArtifactTransformRegistrations(AttributesSchema attributesSchema) {
        this.attributeMatcher = new ArtifactAttributeMatcher(attributesSchema);
    }

    public void registerTransform(Class<? extends ArtifactTransform> type, Action<? super ArtifactTransform> config) {
        ArtifactTransform artifactTransform = DirectInstantiator.INSTANCE.newInstance(type);
        AttributeContainerInternal from = new DefaultAttributeContainer();

        org.gradle.api.internal.artifacts.dsl.dependencies.DefaultArtifactTransformTargets registry = new org.gradle.api.internal.artifacts.dsl.dependencies.DefaultArtifactTransformTargets();
        artifactTransform.configure(from, registry);

        for (AttributeContainerInternal to : registry.getNewTargets()) {
            ArtifactTransformRegistration registration = new ArtifactTransformRegistration(from.asImmutable(), to.asImmutable(), type, config);
            transforms.add(registration);
        }
    }

    private AttributeSpecificCache getCache(@Nullable AttributeContainer attributes) {
        AttributeSpecificCache cache = attributeSpecificCache.get(attributes);
        if (cache == null) {
            cache = new AttributeSpecificCache();
            attributeSpecificCache.put(attributes, cache);
        }
        return cache;
    }

    private boolean matchAttributes(AttributeContainer artifact, AttributeContainer target, boolean incompleteCandidate) {
        Map<AttributeContainer, Boolean> cache;
        if (incompleteCandidate) {
            cache = getCache(target).partialMatches;
        } else {
            cache = getCache(target).exactMatches;
        }

        Boolean match = cache.get(artifact);
        if (match == null) {
            match = attributeMatcher.attributesMatch(artifact, target, incompleteCandidate);
            cache.put(artifact, match);
        }
        return match;
    }

    public boolean areMatchingAttributes(AttributeContainer artifact, AttributeContainer target) {
        return matchAttributes(artifact, target, false);
    }

    public Transformer<List<File>, File> getTransform(AttributeContainer artifact, AttributeContainer target) {
        AttributeSpecificCache toCache = getCache(target.getAttributes());
        Transformer<List<File>, File> transformer = toCache.transforms.get(target);
        if (transformer == null) {
            for (ArtifactTransformRegistration transformReg : transforms) {
                if (matchAttributes(transformReg.from, artifact, true)
                        && matchAttributes(transformReg.to, target, true)) {

                    transformer = transformReg.getTransform();
                    toCache.transforms.put(artifact.getAttributes(), transformer == null ? NO_TRANSFORM : transformer);
                    return transformer;
                }
            }
        }
        return transformer == NO_TRANSFORM ? null : transformer;
    }

    @Override
    public List<ResolvedArtifact> getTransformedArtifacts(ResolvedArtifact artifact, AttributeContainer target) {
        return getCache(target).transformedArtifacts.get(artifact);
    }

    @Override
    public void putTransformedArtifact(ResolvedArtifact artifact, AttributeContainer target, List<ResolvedArtifact> transformResults) {
        getCache(target).transformedArtifacts.put(artifact, transformResults);
    }

    @Override
    public List<File> getTransformedFile(File file, AttributeContainer target) {
        return getCache(target).transformedFiles.get(file);
    }

    @Override
    public void putTransformedFile(File file, AttributeContainer target, List<File> transformResults) {
        getCache(target).transformedFiles.put(file, transformResults);
    }

    private final class ArtifactTransformRegistration {
        public final AttributeContainer from;
        public final AttributeContainer to;
        public final Class<? extends ArtifactTransform> type;
        public final Action<? super ArtifactTransform> config;
        private Transformer<List<File>, File> transform;

        ArtifactTransformRegistration(AttributeContainer from, AttributeContainer to, Class<? extends ArtifactTransform> type, Action<? super ArtifactTransform> config) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.config = config;

            this.transform = createArtifactTransformer();
        }

        public Transformer<List<File>, File> getTransform() {
            return transform;
        }

        private Transformer<List<File>, File> createArtifactTransformer() {
            ArtifactTransform artifactTransform = DirectInstantiator.INSTANCE.newInstance(type);
            config.execute(artifactTransform);
            return new ArtifactFileTransformer(artifactTransform, to);
        }
    }

    private static class ArtifactFileTransformer implements Transformer<List<File>, File> {
        private final ArtifactTransform artifactTransform;
        private final AttributeContainer outputAttributes;

        private ArtifactFileTransformer(ArtifactTransform artifactTransform, AttributeContainer outputAttributes) {
            this.artifactTransform = artifactTransform;
            this.outputAttributes = outputAttributes;
        }

        @Override
        public List<File> transform(File input) {
            if (artifactTransform.getOutputDirectory() != null) {
                artifactTransform.getOutputDirectory().mkdirs();
            }
            List<File> outputs = doTransform(input);
            if (outputs == null) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, new NullPointerException("Illegal null output from ArtifactTransform"));
            }
            for (File output : outputs) {
                if (!output.exists()) {
                    throw new ArtifactTransformException(input, outputAttributes, artifactTransform, new FileNotFoundException("ArtifactTransform output '" + output.getPath() + "' does not exist"));
                }
            }
            return outputs;
        }

        private List<File> doTransform(File input) {
            try {
                return artifactTransform.transform(input, outputAttributes);
            } catch (Exception e) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, e);
            }
        }
    }

    private static class AttributeSpecificCache {
        private final Map<AttributeContainer, Boolean> exactMatches = Maps.newHashMap();
        private final Map<AttributeContainer, Boolean> partialMatches = Maps.newHashMap();
        private final Map<AttributeContainer, Transformer<List<File>, File>> transforms = Maps.newHashMap();
        private final Map<File, List<File>> transformedFiles = Maps.newHashMap();
        private final Map<ResolvedArtifact, List<ResolvedArtifact>> transformedArtifacts = Maps.newHashMap();
    }
}
