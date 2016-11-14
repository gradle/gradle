/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.component.model;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;
import org.gradle.api.AttributeMatchingStrategy;
import org.gradle.api.AttributeValue;
import org.gradle.api.AttributesSchema;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocalComponentDependencyMetadata implements LocalOriginDependencyMetadata {
    private static final Function<ConfigurationMetadata, String> CONFIG_NAME = new Function<ConfigurationMetadata, String>() {
        @Override
        public String apply(ConfigurationMetadata input) {
            return input.getName();
        }
    };
    private static final Function<Attribute<?>, String> ATTRIBUTE_NAME = new Function<Attribute<?>, String>() {
        @Override
        public String apply(Attribute<?> input) {
            return input.getName();
        }
    };

    private final ComponentSelector selector;
    private final ModuleVersionSelector requested;
    private final String moduleConfiguration;
    private final String dependencyConfiguration;
    private final List<Exclude> excludes;
    private final Set<IvyArtifactName> artifactNames;
    private final boolean force;
    private final boolean changing;
    private final boolean transitive;
    private final ModuleExclusion exclusions;
    private final AttributeContainer moduleAttributes;

    public LocalComponentDependencyMetadata(ComponentSelector selector, ModuleVersionSelector requested,
                                            String moduleConfiguration,
                                            AttributeContainer moduleAttributes,
                                            String dependencyConfiguration,
                                            Set<IvyArtifactName> artifactNames, List<Exclude> excludes,
                                            boolean force, boolean changing, boolean transitive) {
        this.selector = selector;
        this.requested = requested;
        this.moduleConfiguration = moduleConfiguration;
        this.moduleAttributes = moduleAttributes;
        this.dependencyConfiguration = dependencyConfiguration;
        this.artifactNames = artifactNames;
        this.excludes = excludes;
        this.exclusions = ModuleExclusions.excludeAny(excludes);
        this.force = force;
        this.changing = changing;
        this.transitive = transitive;
    }

    @Override
    public String toString() {
        return "dependency: " + requested + " from-conf: " + moduleConfiguration + " to-conf: " + dependencyConfiguration;
    }

    @Override
    public ModuleVersionSelector getRequested() {
        return requested;
    }

    @Override
    public ComponentSelector getSelector() {
        return selector;
    }

    @Override
    public String getModuleConfiguration() {
        return moduleConfiguration;
    }

    @Override
    public String getDependencyConfiguration() {
        return getOrDefaultConfiguration(dependencyConfiguration);
    }

    @Override
    public Set<ConfigurationMetadata> selectConfigurations(ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, AttributesSchema attributesSchema) {
        assert fromConfiguration.getHierarchy().contains(getOrDefaultConfiguration(moduleConfiguration));
        AttributeContainer fromConfigurationAttributes = fromConfiguration.getAttributes();
        boolean useConfigurationAttributes = dependencyConfiguration == null && !fromConfigurationAttributes.isEmpty();
        if (useConfigurationAttributes) {
            Matcher matcher = new Matcher(attributesSchema, targetComponent, fromConfigurationAttributes);
            List<ConfigurationMetadata> matches = matcher.getFullMatchs();
            if (matches.size() == 1) {
                return ImmutableSet.of(ClientAttributesPreservingConfigurationMetadata.wrapIfLocal(matches.get(0), fromConfigurationAttributes));
            } else if (!matches.isEmpty()) {
                throwAmbiguousConfigurationSelectionError(fromConfigurationAttributes, matches, true);
            }
            matches = matcher.getPartialMatchs();
            if (matches.size() == 1) {
                return ImmutableSet.of(ClientAttributesPreservingConfigurationMetadata.wrapIfLocal(matches.get(0), fromConfigurationAttributes));
            } else if (!matches.isEmpty()) {
                throwAmbiguousConfigurationSelectionError(fromConfigurationAttributes, matches, false);
            }

        }
        String targetConfiguration = GUtil.elvis(dependencyConfiguration, Dependency.DEFAULT_CONFIGURATION);
        ConfigurationMetadata toConfiguration = targetComponent.getConfiguration(targetConfiguration);
        if (toConfiguration == null) {
            throw new ConfigurationNotFoundException(fromComponent.getComponentId(), moduleConfiguration, targetConfiguration, targetComponent.getComponentId());
        }
        if (dependencyConfiguration != null && !toConfiguration.isCanBeConsumed()) {
            throw new IllegalArgumentException("Configuration '" + dependencyConfiguration + "' cannot be used in a project dependency");
        }
        ConfigurationMetadata delegate = toConfiguration;
        if (useConfigurationAttributes) {
            delegate = ClientAttributesPreservingConfigurationMetadata.wrapIfLocal(delegate, fromConfigurationAttributes);
        }
        return ImmutableSet.of(delegate);
    }

    private static int maxLength(Collection<String> strings) {
        return Ordering.natural().max(Iterables.transform(strings, new Function<String, Integer>() {
            @Override
            public Integer apply(String input) {
                return input.length();
            }
        }));
    }

    private static void throwAmbiguousConfigurationSelectionError(AttributeContainer fromConfigurationAttributes, List<ConfigurationMetadata> matches, boolean fullMatch) {
        Set<String> ambiguousConfigurations = Sets.newTreeSet(Lists.transform(matches, CONFIG_NAME));
        Set<String> requestedAttributes = Sets.newTreeSet(Iterables.transform(fromConfigurationAttributes.keySet(), ATTRIBUTE_NAME));
        StringBuilder sb = new StringBuilder("Cannot choose between the following configurations: ");
        sb.append(ambiguousConfigurations);
        if (fullMatch) {
            sb.append(". All of them match the consumer attributes:");
        } else {
            sb.append(". All of them partially match the consumer attributes:");
        }
        sb.append("\n");
        int maxConfLength = maxLength(ambiguousConfigurations);
        // Sorted for reproducibility
        for (final String ambiguousConf : ambiguousConfigurations) {
            ConfigurationMetadata match = Iterables.find(matches, new Predicate<ConfigurationMetadata>() {
                @Override
                public boolean apply(ConfigurationMetadata input) {
                    return ambiguousConf.equals(input.getName());
                }
            });
            AttributeContainer producerAttributes = match.getAttributes();
            Set<Attribute<?>> targetAttributes = producerAttributes.keySet();
            Set<String> targetAttributeNames = Sets.newTreeSet(Iterables.transform(targetAttributes, ATTRIBUTE_NAME));
            Set<Attribute<?>> allAttributes = Sets.union(fromConfigurationAttributes.keySet(), producerAttributes.keySet());
            Set<String> commonAttributes = Sets.intersection(requestedAttributes, targetAttributeNames);
            Set<String> consumerOnlyAttributes = Sets.difference(requestedAttributes, targetAttributeNames);
            sb.append("   ").append("- Configuration '").append(StringUtils.rightPad(ambiguousConf + "'", maxConfLength + 1)).append(" :");
            List<Attribute<?>> sortedAttributes = Ordering.usingToString().sortedCopy(allAttributes);
            List<String> values = new ArrayList<String>(sortedAttributes.size());
            for (Attribute<?> attribute : sortedAttributes) {
                String attributeName = attribute.getName();
                String label;
                if (commonAttributes.contains(attributeName)) {
                    label = "      - " + "Required " + attributeName + " '" + fromConfigurationAttributes.getAttribute(attribute) + "' and found compatible value '" + producerAttributes.getAttribute(attribute) + "'.";
                } else if (consumerOnlyAttributes.contains(attributeName)) {
                    label = "      - " + "Required " + attributeName + " '" + fromConfigurationAttributes.getAttribute(attribute) + "' but no value provided.";
                } else {
                    label = "      - " + "Found " + attributeName + " '" + producerAttributes.getAttribute(attribute) + "' but wasn't required.";
                }
                values.add(label);

            }
            sb.append("\n");
            sb.append(Joiner.on("\n").join(values));
            sb.append("\n");
        }
        throw new IllegalArgumentException(sb.toString());
    }

    private static String getOrDefaultConfiguration(String configuration) {
        return GUtil.elvis(configuration, Dependency.DEFAULT_CONFIGURATION);
    }

    @Override
    public Set<String> getModuleConfigurations() {
        return ImmutableSet.of(getOrDefaultConfiguration(moduleConfiguration));
    }

    @Override
    public ModuleExclusion getExclusions(ConfigurationMetadata fromConfiguration) {
        assert fromConfiguration.getHierarchy().contains(getOrDefaultConfiguration(moduleConfiguration));
        return exclusions;
    }

    @Override
    public List<Exclude> getExcludes() {
        return excludes;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public boolean isForce() {
        return force;
    }

    @Override
    public String getDynamicConstraintVersion() {
        return requested.getVersion();
    }

    @Override
    public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata fromConfiguration, ConfigurationMetadata toConfiguration) {
        if (artifactNames.isEmpty()) {
            return Collections.emptySet();
        }
        Set<ComponentArtifactMetadata> artifacts = new LinkedHashSet<ComponentArtifactMetadata>();
        for (IvyArtifactName artifactName : artifactNames) {
            artifacts.add(toConfiguration.artifact(artifactName));
        }
        return artifacts;
    }

    @Override
    public Set<IvyArtifactName> getArtifacts() {
        return artifactNames;
    }

    @Override
    public LocalOriginDependencyMetadata withRequestedVersion(String requestedVersion) {
        if (requestedVersion.equals(requested.getVersion())) {
            return this;
        }
        ModuleVersionSelector newRequested = DefaultModuleVersionSelector.newSelector(requested.getGroup(), requested.getName(), requestedVersion);
        ComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(newRequested);
        return copyWithTarget(newSelector, newRequested);
    }

    @Override
    public LocalOriginDependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleVersionSelector requestedVersion = DefaultModuleVersionSelector.newSelector(moduleTarget.getGroup(), moduleTarget.getModule(), moduleTarget.getVersion());
            if (selector.equals(target) && requested.equals(requestedVersion)) {
                return this;
            }
            return copyWithTarget(moduleTarget, requestedVersion);
        } else if (target instanceof ProjectComponentSelector) {
            if (target.equals(selector)) {
                return this;
            }
            return copyWithTarget(target, requested);
        } else {
            throw new AssertionError("Invalid component selector type for substitution: " + target);
        }
    }

    private LocalOriginDependencyMetadata copyWithTarget(ComponentSelector selector, ModuleVersionSelector requested) {
        return new LocalComponentDependencyMetadata(selector, requested, moduleConfiguration, moduleAttributes, dependencyConfiguration, artifactNames, excludes, force, changing, transitive);
    }

    private static class ClientAttributesPreservingConfigurationMetadata implements LocalConfigurationMetadata {
        private final LocalConfigurationMetadata delegate;
        private final AttributeContainer attributes;

        private static ConfigurationMetadata wrapIfLocal(ConfigurationMetadata md, AttributeContainer attributes) {
            if (md instanceof LocalConfigurationMetadata) {
                return new ClientAttributesPreservingConfigurationMetadata((LocalConfigurationMetadata) md, attributes);
            }
            return md;
        }

        private ClientAttributesPreservingConfigurationMetadata(LocalConfigurationMetadata delegate, AttributeContainer attributes) {
            this.delegate = delegate;
            this.attributes = attributes;
        }

        @Override
        public AttributeContainer getAttributes() {
            return attributes;
        }

        @Override
        public boolean isCanBeConsumed() {
            return delegate.isCanBeConsumed();
        }

        @Override
        public boolean isCanBeResolved() {
            return delegate.isCanBeResolved();
        }

        @Override
        public Set<String> getHierarchy() {
            return delegate.getHierarchy();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public List<DependencyMetadata> getDependencies() {
            return delegate.getDependencies();
        }

        @Override
        public Set<ComponentArtifactMetadata> getArtifacts() {
            return delegate.getArtifacts();
        }

        @Override
        public ModuleExclusion getExclusions() {
            return delegate.getExclusions();
        }

        @Override
        public boolean isTransitive() {
            return delegate.isTransitive();
        }

        @Override
        public boolean isVisible() {
            return delegate.isVisible();
        }

        @Override
        public ComponentArtifactMetadata artifact(IvyArtifactName artifact) {
            return delegate.artifact(artifact);
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public Set<String> getExtendsFrom() {
            return delegate.getExtendsFrom();
        }

        @Override
        public TaskDependency getArtifactBuildDependencies() {
            return delegate.getArtifactBuildDependencies();
        }

        @Override
        public Set<LocalFileDependencyMetadata> getFiles() {
            return delegate.getFiles();
        }
    }

    private static class Matcher {
        private final AttributesSchema consumerAttributeSchema;
        private final AttributesSchema producerAttributeSchema;
        private final Map<ConfigurationMetadata, MatchDetails> matchDetails = Maps.newHashMap();
        private final AttributeContainer requestedAttributesContainer;

        public Matcher(AttributesSchema consumerAttributeSchema, ComponentResolveMetadata targetComponent, AttributeContainer requestedAttributesContainer) {
            this.consumerAttributeSchema = consumerAttributeSchema;
            this.producerAttributeSchema = targetComponent instanceof LocalComponentMetadata ? ((LocalComponentMetadata) targetComponent).getAttributesSchema() : null;
            Set<Attribute<?>> requestedAttributeSet = requestedAttributesContainer.keySet();
            for (String config : targetComponent.getConfigurationNames()) {
                ConfigurationMetadata configuration = targetComponent.getConfiguration(config);
                if (configuration.isCanBeConsumed()) {
                    boolean hasAllAttributes = configuration.getAttributes().keySet().containsAll(requestedAttributeSet);
                    matchDetails.put(configuration, new MatchDetails(hasAllAttributes));
                }
            }
            this.requestedAttributesContainer = requestedAttributesContainer;
            doMatch();
        }

        private void doMatch() {
            Set<Attribute<Object>> requestedAttributes = Cast.uncheckedCast(requestedAttributesContainer.keySet());
            for (Map.Entry<ConfigurationMetadata, MatchDetails> entry : matchDetails.entrySet()) {
                ConfigurationMetadata key = entry.getKey();
                MatchDetails details = entry.getValue();
                AttributeContainer dependencyAttributesContainer = key.getAttributes();
                Set<Attribute<Object>> dependencyAttributes = Cast.uncheckedCast(dependencyAttributesContainer.keySet());
                Set<Attribute<Object>> commonAttributes = Sets.intersection(requestedAttributes, dependencyAttributes);
                for (Attribute<Object> attribute : commonAttributes) {
                    AttributeMatchingStrategy<Object> strategy = Cast.uncheckedCast(consumerAttributeSchema.getMatchingStrategy(attribute));
                    try {
                        details.update(attribute, strategy, requestedAttributesContainer.getAttribute(attribute), dependencyAttributesContainer.getAttribute(attribute));
                    } catch (Exception ex) {
                        throw new GradleException("Unexpected error thrown when trying to match attribute values with " + strategy, ex);
                    }
                }
            }
        }

        public List<ConfigurationMetadata> getFullMatchs() {
            List<ConfigurationMetadata> matchs = new ArrayList<ConfigurationMetadata>(1);
            for (Map.Entry<ConfigurationMetadata, MatchDetails> entry : matchDetails.entrySet()) {
                MatchDetails details = entry.getValue();
                if (details.isFullMatch && details.hasAllAttributes) {
                    matchs.add(entry.getKey());
                }
            }
            return disambiguateUsingClosestMatch(matchs);
        }

        private List<ConfigurationMetadata> disambiguateUsingClosestMatch(List<ConfigurationMetadata> matchs) {
            if (matchs.size() > 1) {
                List<ConfigurationMetadata> remainingMatches = selectClosestMatches(matchs);
                if (remainingMatches != null) {
                    return disambiguateUsingProducerSchema(remainingMatches);
                }
            }
            return matchs;
        }

        private List<ConfigurationMetadata> disambiguateUsingProducerSchema(List<ConfigurationMetadata> matchs) {
            if (matchs.size() < 2 || producerAttributeSchema == null) {
                return matchs;
            }
            // If we are reaching this point, it means that we have more than one match, so we need to
            // ask the producer if it has any preference: so far only the consumer schema was used. Now
            // we need to take into consideration the producer schema
            Set<Attribute<?>> producerOnlyAttributes = Sets.newHashSet();
            for (ConfigurationMetadata match : matchs) {
                AttributeContainer attributes = match.getAttributes();
                for (Attribute<?> attribute : attributes.keySet()) {
                    if (!requestedAttributesContainer.contains(attribute)) {
                        producerOnlyAttributes.add(attribute);
                    }
                }
            }
            Set<Attribute<?>> consumerAttributes = consumerAttributeSchema.getAttributes();
            List<ConfigurationMetadata> remainingMatches = Lists.newArrayList(matchs);
            Map<ConfigurationMetadata, Object> values = Maps.newHashMap();
            for (Attribute<?> attribute : producerOnlyAttributes) {
                for (ConfigurationMetadata match : matchs) {
                    Object maybeProvided = match.getAttributes().getAttribute(attribute);
                    if (maybeProvided != null) {
                        values.put(match, maybeProvided);
                    }
                }
                if (!values.isEmpty()) {
                    AttributeMatchingStrategy<Object> matchingStrategy = Cast.uncheckedCast(producerAttributeSchema.getMatchingStrategy(attribute));
                    AttributeValue<Object> absent = consumerAttributes.contains(attribute) ? AttributeValue.missing() : AttributeValue.unknown();
                    List<ConfigurationMetadata> best = matchingStrategy.selectClosestMatch(absent, values);
                    remainingMatches.retainAll(best);
                    if (remainingMatches.isEmpty()) {
                        // the intersection is empty, so we cannot choose
                        return matchs;
                    }
                    values.clear();
                }
            }
            if (!remainingMatches.isEmpty()) {
                // there's a subset (or not) of best matches
                return remainingMatches;
            }
            return matchs;
        }

        public List<ConfigurationMetadata> getPartialMatchs() {
            List<ConfigurationMetadata> matchs = new ArrayList<ConfigurationMetadata>(1);
            for (Map.Entry<ConfigurationMetadata, MatchDetails> entry : matchDetails.entrySet()) {
                MatchDetails details = entry.getValue();
                if (details.isPartialMatch && !details.hasAllAttributes) {
                    matchs.add(entry.getKey());
                }
            }
            return disambiguateUsingClosestMatch(matchs);
        }

        private List<ConfigurationMetadata> selectClosestMatches(List<ConfigurationMetadata> fullMatches) {
            Set<Attribute<?>> requestedAttributes = requestedAttributesContainer.keySet();
            // if there's more than one compatible match, prefer the closest. However there's a catch.
            // We need to look at all candidates globally, and select the closest match for each attribute
            // then see if there's a non-empty intersection.
            List<ConfigurationMetadata> remainingMatches = Lists.newArrayList(fullMatches);
            Map<ConfigurationMetadata, Object> values = Maps.newHashMap();
            for (Attribute<?> attribute : requestedAttributes) {
                Object requestedValue = requestedAttributesContainer.getAttribute(attribute);
                for (ConfigurationMetadata match : fullMatches) {
                    Map<Attribute<Object>, Object> matchedAttributes = matchDetails.get(match).matchesByAttribute;
                    values.put(match, matchedAttributes.get(attribute));
                }
                AttributeMatchingStrategy<Object> matchingStrategy = Cast.uncheckedCast(consumerAttributeSchema.getMatchingStrategy(attribute));
                List<ConfigurationMetadata> best = matchingStrategy.selectClosestMatch(AttributeValue.of(requestedValue), values);
                remainingMatches.retainAll(best);
                if (remainingMatches.isEmpty()) {
                    // the intersection is empty, so we cannot choose
                    return fullMatches;
                }
                values.clear();
            }
            if (!remainingMatches.isEmpty()) {
                // there's a subset (or not) of best matches
                return remainingMatches;
            }
            return null;
        }

    }

    private static class MatchDetails {
        private final Map<Attribute<Object>, Object> matchesByAttribute = Maps.newHashMap();
        private final boolean hasAllAttributes;

        private boolean isFullMatch;
        private boolean isPartialMatch;

        private MatchDetails(boolean hasAllAttributes) {
            this.hasAllAttributes = hasAllAttributes;
            this.isFullMatch = hasAllAttributes;
        }

        private void update(Attribute<Object> attribute, AttributeMatchingStrategy<Object> strategy, Object requested, Object provided) {
            boolean attributeCompatible = strategy.isCompatible(requested, provided);
            if (attributeCompatible) {
                matchesByAttribute.put(attribute, provided);
            }
            isFullMatch &= attributeCompatible;
            isPartialMatch |= attributeCompatible;
        }
    }
}
