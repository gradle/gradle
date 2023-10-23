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

package org.gradle.api.tasks.diagnostics.internal.configurations.model;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.attributes.DefaultCompatibilityRuleChain;
import org.gradle.api.internal.attributes.DefaultDisambiguationRuleChain;
import org.gradle.api.internal.file.FileResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for creating {@link ConfigurationReportModel} instances which represent the configurations present in a project.
 */
public final class ConfigurationReportModelFactory {
    private final FileResolver fileResolver;

    public ConfigurationReportModelFactory(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public ConfigurationReportModel buildForProject(Project project) {
        final ConfigurationRuleScanner scanner = new ConfigurationRuleScanner(project);
        final List<ReportAttribute> attributesWithCompatibilityRules = scanner.getAttributesWithCompatibilityRules();
        final List<ReportAttribute> attributesWithDisambiguationRules = scanner.getAttributesWithDisambiguationRules();

        final Map<String, ReportConfiguration> convertedConfigurations = new HashMap<>(project.getConfigurations().size());
        project.getConfigurations().stream()
            .map(ConfigurationInternal.class::cast)
            .forEach(configuration -> getOrConvert(configuration, project, convertedConfigurations));

        return new ConfigurationReportModel(project.getName(),
            convertedConfigurations.values().stream().sorted(Comparator.comparing(ReportConfiguration::getName)).collect(Collectors.toList()),
            attributesWithCompatibilityRules, attributesWithDisambiguationRules);
    }

    private ReportConfiguration getOrConvert(ConfigurationInternal configuration, Project project, Map<String, ReportConfiguration> convertedConfigurations) {
        return computeIfAbsentExternal(convertedConfigurations, configuration.getName(), name -> {
            final List<ReportConfiguration> extendedConfigurations = new ArrayList<>(configuration.getExtendsFrom().size());
            configuration.getExtendsFrom().stream()
                .map(ConfigurationInternal.class::cast)
                .forEach(c -> extendedConfigurations.add(getOrConvert(c, project, convertedConfigurations)));

            return convertConfiguration(configuration, project, fileResolver, extendedConfigurations);
        });
    }

    private ReportConfiguration convertConfiguration(ConfigurationInternal configuration, Project project, FileResolver fileResolver, List<ReportConfiguration> extendedConfigurations) {
        // Important to lock the config prior to extracting the attributes, as some attributes, such as TargetJvmVersion, are actually added by this locking process
        List<? extends GradleException> lenientErrors = configuration.preventFromFurtherMutationLenient();

        final List<ReportAttribute> attributes = configuration.getAttributes().keySet().stream()
            .map(a -> convertAttributeInContainer(a, configuration.getAttributes(), project.getDependencies().getAttributesSchema()))
            .sorted(Comparator.comparing(ReportAttribute::getName))
            .collect(Collectors.toList());

        final List<ReportCapability> capabilities = getConfigurationCapabilities(configuration, project);

        final List<ReportArtifact> artifacts = configuration.getAllArtifacts().stream()
            .map(a -> convertPublishArtifact(a, fileResolver))
            .sorted(Comparator.comparing(ReportArtifact::getDisplayName))
            .collect(Collectors.toList());

        final List<ReportSecondaryVariant> variants = getConfigurationArtifactVariants(configuration, project, fileResolver);

        final ReportConfiguration.Type type;
        if (configuration.isCanBeConsumed() && configuration.isCanBeResolved()) {
            type = ReportConfiguration.Type.LEGACY;
        } else if (configuration.isCanBeResolved()) {
            type = ReportConfiguration.Type.RESOLVABLE;
        } else if (configuration.isCanBeConsumed()) {
            type = ReportConfiguration.Type.CONSUMABLE;
        } else {
            type = null;
        }

        return new ReportConfiguration(configuration.getName(), configuration.getDescription(), type, new ArrayList<>(lenientErrors),
            attributes, capabilities, artifacts, variants, extendedConfigurations);
    }

    private List<ReportCapability> getConfigurationCapabilities(ConfigurationInternal configuration, Project project) {
        if (!configuration.isCanBeConsumed()) {
            return Collections.emptyList();
        }

        final List<ReportCapability> explicitCapabilities = configuration.getCapabilitiesInternal().stream()
            .map(this::convertCapability)
            .sorted(Comparator.comparing(ReportCapability::toGAV))
            .collect(Collectors.toList());

        if (explicitCapabilities.isEmpty()) {
            return Collections.singletonList(convertDefaultCapability(project));
        } else {
            return explicitCapabilities;
        }
    }

    private List<ReportSecondaryVariant> getConfigurationArtifactVariants(ConfigurationInternal configuration, Project project, FileResolver fileResolver) {
        if (!configuration.isCanBeConsumed()) {
            return Collections.emptyList();
        }

        return configuration.getOutgoing().getVariants().stream()
            .map(v -> convertConfigurationVariant(v, fileResolver, project.getDependencies().getAttributesSchema()))
            .sorted(Comparator.comparing(ReportSecondaryVariant::getName))
            .collect(Collectors.toList());
    }

    private ReportArtifact convertPublishArtifact(PublishArtifact publishArtifact, FileResolver fileResolver) {
        return new ReportArtifact(publishArtifact.getName(), fileResolver.resolveForDisplay(publishArtifact.getFile()), publishArtifact.getClassifier(), publishArtifact.getType());
    }

    private ReportSecondaryVariant convertConfigurationVariant(ConfigurationVariant variant, FileResolver fileResolver, AttributesSchema attributesSchema) {
        final List<ReportAttribute> attributes = Collections.unmodifiableList(variant.getAttributes().keySet().stream()
            .map(a -> convertAttributeInContainer(a, variant.getAttributes(), attributesSchema))
            .sorted(Comparator.comparing(ReportAttribute::getName))
            .collect(Collectors.toList()));
        final List<ReportArtifact> artifacts = Collections.unmodifiableList(variant.getArtifacts().stream()
            .map(publishArtifact -> convertPublishArtifact(publishArtifact, fileResolver))
            .sorted(Comparator.comparing(ReportArtifact::getName))
            .collect(Collectors.toList()));

        return new ReportSecondaryVariant(variant.getName(), variant.getDescription().orElse(null), attributes, artifacts);
    }

    private ReportAttribute convertAttributeInContainer(Attribute<?> attribute, AttributeContainer container, AttributesSchema attributesSchema) {
        @SuppressWarnings("unchecked") Attribute<Object> key = (Attribute<Object>) attribute;
        Object value = container.getAttribute(key);
        return new ReportAttribute(key, value, getDisambiguationPrecedence(attribute, attributesSchema).orElse(null));
    }

    private ReportAttribute convertUncontainedAttribute(Attribute<?> attribute, AttributesSchema attributesSchema) {
        @SuppressWarnings("unchecked") Attribute<Object> key = (Attribute<Object>) attribute;
        return new ReportAttribute(key, null, getDisambiguationPrecedence(attribute, attributesSchema).orElse(null));
    }

    private Optional<Integer> getDisambiguationPrecedence(Attribute<?> attribute, AttributesSchema attributesSchema) {
        int index = attributesSchema.getAttributeDisambiguationPrecedence().indexOf(attribute);
        return index == -1 ? Optional.empty() : Optional.of(index + 1); // return ordinals, not indices
    }

    private ReportCapability convertCapability(Capability capability) {
        return new ReportCapability(capability.getGroup(), capability.getName(), capability.getVersion(), false);
    }

    private ReportCapability convertDefaultCapability(Project project) {
        return new ReportCapability(Objects.toString(project.getGroup()), project.getName(), Objects.toString(project.getVersion()), true);
    }

    /**
     * This class can examine a project to determine which {@link Attribute}s in its schema have {@link org.gradle.api.internal.attributes.CompatibilityRule CompatibilityRule}s or
     * {@link org.gradle.api.internal.attributes.DisambiguationRule DisambiguationRule}s defined.
     */
    private final class ConfigurationRuleScanner {
        private final AttributesSchema attributesSchema;

        public ConfigurationRuleScanner(Project project) {
            attributesSchema = project.getDependencies().getAttributesSchema();
        }

        public List<ReportAttribute> getAttributesWithCompatibilityRules() {
            return attributesSchema.getAttributes().stream()
                .filter(this::hasCompatibilityRules)
                .map(a -> convertUncontainedAttribute(a, attributesSchema))
                .sorted(Comparator.comparing(ReportAttribute::getName))
                .collect(Collectors.toList());
        }

        public List<ReportAttribute> getAttributesWithDisambiguationRules() {
            return attributesSchema.getAttributes().stream()
                .filter(this::hasDisambiguationRules)
                .map(a -> convertUncontainedAttribute(a, attributesSchema))
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

    /**
     * Recursively calling {@link Map#computeIfAbsent(Object, Function)} to insert multiple keys could pose issues, depending on the implementation of the underlying map.
     *
     * This method exists to externalize that usage and avoid the potential for these sort of issues.
     */
    private static <K, V> V computeIfAbsentExternal(Map<K, V> map, K key, Function<? super K, ? extends V> mappingFunction) {
        if (map.containsKey(key)) {
            return map.get(key);
        } else {
            V v = mappingFunction.apply(key);
            map.put(key, v);
            return v;
        }
    }
}
