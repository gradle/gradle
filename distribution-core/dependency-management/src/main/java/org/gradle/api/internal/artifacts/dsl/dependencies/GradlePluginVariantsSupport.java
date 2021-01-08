/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.util.GradleVersion;

public class GradlePluginVariantsSupport {

    public static void configureSchema(AttributesSchema attributesSchema) {
        AttributeMatchingStrategy<GradlePluginApiVersion> strategy = attributesSchema.attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE);
        strategy.getCompatibilityRules().add(TargetGradleVersionCompatibilityRule.class);
        strategy.getDisambiguationRules().add(TargetGradleVersionDisambiguationRule.class);
    }

    public static class TargetGradleVersionCompatibilityRule implements AttributeCompatibilityRule<GradlePluginApiVersion> {

        @Override
        public void execute(CompatibilityCheckDetails<GradlePluginApiVersion> details) {
            // we compare to the base version of the consumer, because pre-release versions should already match variants targeting the final release
            GradlePluginApiVersion consumer = details.getConsumerValue();
            GradlePluginApiVersion producer = details.getProducerValue();
            if (consumer == null || producer == null) {
                details.compatible();
            } else if (GradleVersion.version(consumer.getName()).getBaseVersion().compareTo(GradleVersion.version(producer.getName())) >= 0) {
                details.compatible();
            } else {
                details.incompatible();
            }
        }
    }

    public static class TargetGradleVersionDisambiguationRule implements AttributeDisambiguationRule<GradlePluginApiVersion> {

        @Override
        public void execute(MultipleCandidatesDetails<GradlePluginApiVersion> details) {
            GradlePluginApiVersion consumerValue = details.getConsumerValue();
            GradleVersion consumer = consumerValue == null ? GradleVersion.current() : GradleVersion.version(consumerValue.getName());
            GradleVersion bestMatchVersion = GradleVersion.version("0.0");
            GradlePluginApiVersion bestMatchAttribute = null;

            for(GradlePluginApiVersion candidate : details.getCandidateValues()) {
                GradleVersion producer = GradleVersion.version(candidate.getName());
                if (producer.compareTo(consumer) <= 0 && producer.compareTo(bestMatchVersion) > 0) {
                    bestMatchVersion = producer;
                    bestMatchAttribute = candidate;
                }
            }
            if (bestMatchAttribute != null) {
                details.closestMatch(bestMatchAttribute);
            }
        }
    }
}
