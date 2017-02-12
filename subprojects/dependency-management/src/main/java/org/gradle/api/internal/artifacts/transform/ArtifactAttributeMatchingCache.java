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

import java.io.File;
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
        return matchAttributes(actual, requested, false);
    }

    @Nullable
    public Transformer<List<File>, File> getTransform(AttributeContainer actual, AttributeContainer requested) {
        AttributeSpecificCache toCache = getCache(requested);
        Transformer<List<File>, File> transformer = toCache.transforms.get(actual);
        if (transformer == null) {
            for (ArtifactTransformRegistration transformReg : artifactTransformRegistrations.getTransforms()) {
                if (matchAttributes(transformReg.getFrom(), actual, true)
                    && matchAttributes(transformReg.getTo(), requested, true)) {

                    transformer = transformReg.getTransform();
                    toCache.transforms.put(actual, transformer);
                    return transformer;
                }
            }
            toCache.transforms.put(actual, NO_TRANSFORM);
        }
        return transformer == NO_TRANSFORM ? null : transformer;
    }

    private AttributeSpecificCache getCache(AttributeContainer attributes) {
        AttributeSpecificCache cache = attributeSpecificCache.get(attributes);
        if (cache == null) {
            cache = new AttributeSpecificCache();
            attributeSpecificCache.put(attributes, cache);
        }
        return cache;
    }

    private boolean matchAttributes(AttributeContainer actual, AttributeContainer requested, boolean incompleteCandidate) {
        Map<AttributeContainer, Boolean> cache;
        if (incompleteCandidate) {
            cache = getCache(requested).partialMatches;
        } else {
            cache = getCache(requested).exactMatches;
        }

        Boolean match = cache.get(actual);
        if (match == null) {
            if (actual.isEmpty() && requested.isEmpty()) {
                match = true;
            } else {
                match = schema.isMatching(actual, requested, incompleteCandidate);
            }
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
        private final Map<AttributeContainer, Boolean> exactMatches = Maps.newConcurrentMap();
        private final Map<AttributeContainer, Boolean> partialMatches = Maps.newConcurrentMap();
        private final Map<AttributeContainer, Transformer<List<File>, File>> transforms = Maps.newConcurrentMap();
        private final Map<File, List<File>> transformedFiles = Maps.newConcurrentMap();
        private final Map<ResolvedArtifact, List<ResolvedArtifact>> transformedArtifacts = Maps.newConcurrentMap();
    }
}
