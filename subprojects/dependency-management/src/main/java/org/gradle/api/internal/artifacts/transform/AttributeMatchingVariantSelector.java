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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.Pair;
import org.gradle.internal.component.AmbiguousVariantSelectionException;
import org.gradle.internal.component.NoMatchingVariantSelectionException;
import org.gradle.internal.component.VariantSelectionException;
import org.gradle.internal.component.model.AttributeMatcher;

import java.util.ArrayList;
import java.util.List;

class AttributeMatchingVariantSelector implements VariantSelector {
    private final ConsumerProvidedVariantFinder consumerProvidedVariantFinder;
    private final AttributesSchemaInternal schema;
    private final AttributeContainerInternal requested;
    private final boolean ignoreWhenNoMatches;

    AttributeMatchingVariantSelector(ConsumerProvidedVariantFinder consumerProvidedVariantFinder, AttributesSchemaInternal schema, AttributeContainerInternal requested, boolean ignoreWhenNoMatches) {
        this.consumerProvidedVariantFinder = consumerProvidedVariantFinder;
        this.schema = schema;
        this.requested = requested;
        this.ignoreWhenNoMatches = ignoreWhenNoMatches;
    }

    @Override
    public String toString() {
        return "Variant selector for " + requested;
    }

    @Override
    public ResolvedArtifactSet select(ResolvedVariantSet producer) {
        try {
            return doSelect(producer);
        } catch (VariantSelectionException t) {
            return new BrokenResolvedArtifactSet(t);
        } catch (Throwable t) {
            return new BrokenResolvedArtifactSet(VariantSelectionException.selectionFailed(producer, t));
        }
    }

    private ResolvedArtifactSet doSelect(ResolvedVariantSet producer) {
        AttributeMatcher matcher = schema.withProducer(producer.getSchema());
        List<? extends ResolvedVariant> matches = matcher.matches(producer.getVariants(), requested);
        if (matches.size() == 1) {
            return matches.get(0).getArtifacts();
        }
        if (matches.size() > 1) {
            throw new AmbiguousVariantSelectionException(producer.asDescribable().getDisplayName(), requested, matches, matcher);
        }

        List<Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant>> candidates = new ArrayList<Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant>>();
        for (ResolvedVariant variant : producer.getVariants()) {
            AttributeContainerInternal variantAttributes = variant.getAttributes().asImmutable();
            ConsumerVariantMatchResult matchResult = new ConsumerVariantMatchResult();
            consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requested, matchResult);
            for (ConsumerVariantMatchResult.ConsumerVariant consumerVariant : matchResult.getMatches()) {
                candidates.add(Pair.of(variant, consumerVariant));
            }
        }
        if (candidates.size() == 1) {
            Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant> result = candidates.get(0);
            return new ConsumerProvidedResolvedVariant(result.getLeft().getArtifacts(), result.getRight().attributes, result.getRight().transformer);
        }

        if (!candidates.isEmpty()) {
            throw new AmbiguousTransformException(producer.asDescribable().getDisplayName(), requested, candidates);
        }

        if (ignoreWhenNoMatches) {
            return ResolvedArtifactSet.EMPTY;
        }
        throw new NoMatchingVariantSelectionException(producer.asDescribable().getDisplayName(), requested, producer.getVariants(), matcher);
    }
}
