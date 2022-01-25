/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.configurations;

import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.attributes.DefaultCompatibilityRuleChain;
import org.gradle.api.internal.attributes.DefaultDisambiguationRuleChain;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportAttribute;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class can examine a project to deterimne which {@link Attribute}s in its schema have {@link org.gradle.api.internal.attributes.CompatibilityRule CompatibilityRule}s or
 * {@link org.gradle.api.internal.attributes.DisambiguationRule DisambiguationRule}s defined.
 */
public final class ConfigurationRuleScanner {
    private final AttributesSchema attributesSchema;

    public ConfigurationRuleScanner(Project project) {
        attributesSchema = project.getDependencies().getAttributesSchema();
    }

    public List<ReportAttribute> getAttributesWithCompatibilityRules() {
        return attributesSchema.getAttributes().stream()
            .filter(this::hasCompatibilityRules)
            .map(ReportAttribute::fromUncontainedAttribute)
            .sorted(Comparator.comparing(ReportAttribute::getName))
            .collect(Collectors.toList());
    }

    public List<ReportAttribute> getAttributesWithDisambiguationRules() {
        return attributesSchema.getAttributes().stream()
            .filter(this::hasDisambiguationRules)
            .map(ReportAttribute::fromUncontainedAttribute)
            .sorted(Comparator.comparing(ReportAttribute::getName))
            .collect(Collectors.toList());
    }

    private boolean hasCompatibilityRules(Attribute<?> attribute) {
        final AttributeMatchingStrategy<?> matchingStrategy = attributesSchema.getMatchingStrategy(attribute);
        final DefaultCompatibilityRuleChain<?> ruleChain = (DefaultCompatibilityRuleChain<?>) matchingStrategy.getCompatibilityRules();
        return ruleChain.doesSomething();
    }

    private boolean hasDisambiguationRules(Attribute<?> attribute) {
        final AttributeMatchingStrategy<?> matchingStrategy = attributesSchema.getMatchingStrategy(attribute);
        final DefaultDisambiguationRuleChain<?> ruleChain = (DefaultDisambiguationRuleChain<?>) matchingStrategy.getDisambiguationRules();
        return ruleChain.doesSomething();
    }
}
