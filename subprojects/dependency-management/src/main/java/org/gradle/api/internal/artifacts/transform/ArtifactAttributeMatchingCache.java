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

import com.google.common.collect.Maps;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.model.AttributeMatcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ArtifactAttributeMatchingCache {
    private final ArtifactTransformRegistrationsInternal artifactTransformRegistrations;
    private final AttributesSchemaInternal schema;
    private final Map<AttributeContainer, AttributeSpecificCache> attributeSpecificCache = Maps.newConcurrentMap();

    private static final Transformer<List<File>, File> NO_TRANSFORM = new Transformer<List<File>, File>() {
        @Override
        public List<File> transform(File file) {
            return null;
        }
    };

    public ArtifactAttributeMatchingCache(ArtifactTransformRegistrationsInternal artifactTransformRegistrations, AttributesSchemaInternal schema) {
        this.artifactTransformRegistrations = artifactTransformRegistrations;
        this.schema = schema;
    }

    public boolean areMatchingAttributes(AttributeContainer actual, AttributeContainer requested) {
        return matchAttributes(actual, requested, true);
    }

    @Nullable
    public Transformer<List<File>, File> getTransform(AttributeContainer actual, AttributeContainer requested) {
        AttributeSpecificCache toCache = getCache(requested);
        Transformer<List<File>, File> transformer = toCache.transforms.get(actual);
        if (transformer == null) {
            transformer = findProducerFor(actual, requested);
            toCache.transforms.put(actual, transformer);
        }
        return transformer == NO_TRANSFORM ? null : transformer;
    }

    private Transformer<List<File>, File> findProducerFor(AttributeContainer actual, AttributeContainer requested) {
        // Prefer direct transformation over indirect transformation
        List<ArtifactTransformRegistration> candidates = new ArrayList<ArtifactTransformRegistration>();
        for (ArtifactTransformRegistration transform : artifactTransformRegistrations.getTransforms()) {
            if (matchAttributes(transform.getTo(), requested, false)) {
                if (matchAttributes(actual, transform.getFrom(), true)) {
                    return transform.getTransform();
                }
                candidates.add(transform);
            }
        }
        for (final ArtifactTransformRegistration candidate : candidates) {
            final Transformer<List<File>, File> inputProducer = getTransform(actual, candidate.getFrom());
            if (inputProducer != null) {
                return new Transformer<List<File>, File>() {
                    @Override
                    public List<File> transform(File file) {
                        List<File> result = new ArrayList<File>();
                        for (File intermediate : inputProducer.transform(file)) {
                            result.addAll(candidate.getTransform().transform(intermediate));
                        }
                        return result;
                    }
                };
            }
        }
        return NO_TRANSFORM;
    }

    private AttributeSpecificCache getCache(AttributeContainer attributes) {
        AttributeSpecificCache cache = attributeSpecificCache.get(attributes);
        if (cache == null) {
            cache = new AttributeSpecificCache();
            attributeSpecificCache.put(attributes, cache);
        }
        return cache;
    }

    private boolean matchAttributes(AttributeContainer actual, AttributeContainer requested, boolean ignoreAdditionalActualAttributes) {
        Map<AttributeContainer, Boolean> cache;
        AttributeMatcher schemaToMatchOn;
        if (ignoreAdditionalActualAttributes) {
            if (requested.isEmpty()) {
                return true;
            }
            schemaToMatchOn = schema.ignoreAdditionalProducerAttributes();
            cache = getCache(requested).ignoreExtraActual;
        } else { // ignore additional requested
            if (actual.isEmpty()) {
                return true;
            }
            schemaToMatchOn = schema.ignoreAdditionalConsumerAttributes();
            cache = getCache(requested).ignoreExtraRequested;
        }

        Boolean match = cache.get(actual);
        if (match == null) {
            match = schemaToMatchOn.isMatching(actual, requested);
            cache.put(actual, match);
        }
        return match;
    }

    public List<ResolvedArtifact> getTransformedArtifacts(ResolvedArtifact actual, AttributeContainer requested) {
        return getCache(requested).transformedArtifacts.get(actual);
    }

    public void putTransformedArtifact(ResolvedArtifact actual, AttributeContainer requested, List<ResolvedArtifact> transformResults) {
        getCache(requested).transformedArtifacts.put(actual, transformResults);
    }

    public List<File> getTransformedFile(File file, AttributeContainer requested) {
        return getCache(requested).transformedFiles.get(file);
    }

    public void putTransformedFile(File file, AttributeContainer requested, List<File> transformResults) {
        getCache(requested).transformedFiles.put(file, transformResults);
    }

    private static class AttributeSpecificCache {
        private final Map<AttributeContainer, Boolean> ignoreExtraRequested = Maps.newConcurrentMap();
        private final Map<AttributeContainer, Boolean> ignoreExtraActual = Maps.newConcurrentMap();
        private final Map<AttributeContainer, Transformer<List<File>, File>> transforms = Maps.newConcurrentMap();
        private final Map<File, List<File>> transformedFiles = Maps.newConcurrentMap();
        private final Map<ResolvedArtifact, List<ResolvedArtifact>> transformedArtifacts = Maps.newConcurrentMap();
    }
}
