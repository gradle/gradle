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
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Set;

/**
 * Wires the {@link FallbackVariant#FALLBACK_VARIANT_ATTRIBUTE} attribute into the
 * universal attributes schema.
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

    public void configureSchema(AttributesSchemaInternal attributesSchema) {
        AttributeMatchingStrategy<FallbackVariant> strategy = attributesSchema.attribute(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE);
        strategy.getDisambiguationRules().add(FallbackVariantDisambiguationRule.class);
    }

    @VisibleForTesting
    static class FallbackVariantDisambiguationRule implements AttributeDisambiguationRule<FallbackVariant> {
        @Override
        public void execute(MultipleCandidatesDetails<FallbackVariant> details) {
            FallbackVariant consumerValue = details.getConsumerValue();
            Set<FallbackVariant> candidateValues = details.getCandidateValues();

            // Honour an explicit consumer request, providing the escape hatch for
            // consumers that want the fallback primary.
            if (consumerValue != null && candidateValues.contains(consumerValue)) {
                details.closestMatch(consumerValue);
                return;
            }

            // Prefer FALSE over TRUE when the consumer didn't pin a value.
            for (FallbackVariant candidate : candidateValues) {
                if (FallbackVariant.FALSE.equals(candidate.getName())) {
                    details.closestMatch(candidate);
                    return;
                }
            }
        }
    }
}
