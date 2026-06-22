/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.FallbackVariant;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.Set;

/**
 * Wires the {@link FallbackVariant#FALLBACK_VARIANT_ATTRIBUTE} attribute into the
 * universal attributes schema, and provides the consumer-side default injection
 * that pairs with the producer-side auto-tagging in
 * {@code DefaultConfigurationPublications#addFallbackIfNecessary}.
 * <p>
 * The default disambiguation rule prefers {@link FallbackVariant#FALSE} over
 * {@link FallbackVariant#TRUE} when both values are candidates, so that "real"
 * (non-fallback) variants win over an empty primary that has been auto-tagged
 * as a fallback. When the consumer explicitly requests a specific fallback-variant
 * value the rule honours it &mdash; this provides the escape hatch that lets a
 * consumer opt into the fallback primary.
 */
@ServiceScope(Scope.Global.class)
public final class FallbackVariantSupport {

    private final FallbackVariant falseValue;

    @Inject
    public FallbackVariantSupport(NamedObjectInstantiator namedObjectInstantiator) {
        this.falseValue = namedObjectInstantiator.named(FallbackVariant.class, FallbackVariant.FALSE);
    }

    public void configureSchema(AttributesSchemaInternal attributesSchema) {
        AttributeMatchingStrategy<FallbackVariant> strategy = attributesSchema.attribute(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE);
        strategy.getDisambiguationRules().add(FallbackVariantDisambiguationRule.class);
    }

    /**
     * Augments a consumer's attribute set with a default {@link FallbackVariant#FALSE} value
     * for {@link FallbackVariant#FALLBACK_VARIANT_ATTRIBUTE} if the consumer hasn't already
     * requested a value.
     * <p>
     * This is the consumer-side counterpart to the producer-side auto-tagging in
     * {@code DefaultConfigurationPublications#addFallbackIfNecessary}. Together they ensure
     * that an empty primary tagged {@link FallbackVariant#TRUE} is value-incompatible with
     * a silent consumer's effective request and gets pruned at the matcher's compatibility
     * check &mdash; before the matcher would otherwise pick it as the sole "directly
     * compatible" candidate (because the primary's silence on a transformable attribute
     * like {@code artifactType} is treated as compatible by default).
     * <p>
     * Must be invoked at every site that hands consumer attributes to
     * {@code AttributeMatcher#matchMultipleCandidates}.
     *
     * @param consumerAttributes the consumer-side attribute set
     * @param attributesFactory factory used to build the augmented set
     * @return {@code consumerAttributes} unchanged if already carries a value, otherwise augmented
     */
    public ImmutableAttributes augmentConsumerWithDefault(
        ImmutableAttributes consumerAttributes,
        AttributesFactory attributesFactory
    ) {
        if (consumerAttributes.findEntry(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE.getName()) != null) {
            return consumerAttributes;
        }
        return attributesFactory.concat(consumerAttributes, FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE, falseValue);
    }

    @VisibleForTesting
    static class FallbackVariantDisambiguationRule implements AttributeDisambiguationRule<FallbackVariant> {
        @Override
        public void execute(MultipleCandidatesDetails<FallbackVariant> details) {
            FallbackVariant consumerValue = details.getConsumerValue();
            Set<FallbackVariant> candidateValues = details.getCandidateValues();

            // Honour any explicit consumer request. This is also the escape hatch
            // that lets a consumer opt into the fallback primary by requesting TRUE.
            if (consumerValue != null && candidateValues.contains(consumerValue)) {
                details.closestMatch(consumerValue);
                return;
            }

            // Silent consumer: prefer FALSE over TRUE so the non-fallback (secondary)
            // wins over an empty primary that was auto-tagged as a fallback.
            for (FallbackVariant candidate : candidateValues) {
                if (FallbackVariant.FALSE.equals(candidate.getName())) {
                    details.closestMatch(candidate);
                    return;
                }
            }
        }
    }
}
