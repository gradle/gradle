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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.transform.ArtifactTransformRegistrations;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;

import java.io.File;
import java.util.List;
import java.util.Map;

public class ArtifactAttributeMatchingCache {

    private final ArtifactAttributeMatcher attributeMatcher;
    private final ArtifactTransformRegistrationsInternal artifactTransformRegistrations;

    private final Map<AttributeContainer, AttributeSpecificCache> attributeSpecificCache = Maps.newConcurrentMap();

    private static final Transformer<List<File>, File> NO_TRANSFORM = new Transformer<List<File>, File>() {
        @Override
        public List<File> transform(File file) {
            return null;
        }
    };

    public ArtifactAttributeMatchingCache(ArtifactTransformRegistrations artifactTransformRegistrations, AttributesSchema schema) {
        this.attributeMatcher = new ArtifactAttributeMatcher(schema);
        this.artifactTransformRegistrations = (ArtifactTransformRegistrationsInternal) artifactTransformRegistrations;
    }
    boolean areMatchingAttributes(AttributeContainer artifact, AttributeContainer target) {
        return matchAttributes(artifact, target, false);
    }

    Transformer<List<File>, File> getTransform(AttributeContainer artifact, AttributeContainer target) {
        AttributeSpecificCache toCache = getCache(target.getAttributes());
        Transformer<List<File>, File> transformer = toCache.transforms.get(target);
        if (transformer == null) {
            for (ArtifactTransformRegistration transformReg : artifactTransformRegistrations.getTransforms()) {
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

    private AttributeSpecificCache getCache(AttributeContainer attributes) {
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

    List<ResolvedArtifact> getTransformedArtifacts(ResolvedArtifact artifact, AttributeContainer target) {
        return getCache(target).transformedArtifacts.get(artifact);
    }

    void putTransformedArtifact(ResolvedArtifact artifact, AttributeContainer target, List<ResolvedArtifact> transformResults) {
        getCache(target).transformedArtifacts.put(artifact, transformResults);
    }

    List<File> getTransformedFile(File file, AttributeContainer target) {
        return getCache(target).transformedFiles.get(file);
    }

    void putTransformedFile(File file, AttributeContainer target, List<File> transformResults) {
        getCache(target).transformedFiles.put(file, transformResults);
    }

    private static class AttributeSpecificCache {
        private final Map<AttributeContainer, Boolean> exactMatches = Maps.newConcurrentMap();
        private final Map<AttributeContainer, Boolean> partialMatches = Maps.newConcurrentMap();
        private final Map<AttributeContainer, Transformer<List<File>, File>> transforms = Maps.newConcurrentMap();
        private final Map<File, List<File>> transformedFiles = Maps.newConcurrentMap();
        private final Map<ResolvedArtifact, List<ResolvedArtifact>> transformedArtifacts = Maps.newConcurrentMap();
    }
}
