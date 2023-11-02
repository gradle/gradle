/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.artifacts.transform.AmbiguousArtifactTransformException;
import org.gradle.api.internal.artifacts.transform.AttributeMatchingArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.problems.Problems;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.gradle.internal.exceptions.StyledException.style;

// TODO: Register all failures with the Problems API

/**
 * Provides a central location for logging and reporting failures appearing during
 * each stage of resolution, including during the variant selection process.
 *
 * All variant selection failures encountered during selection by the {@link GraphVariantSelector} or
 * {@link AttributeMatchingArtifactVariantSelector}
 * should be routed through this class.
 *
 * This class reports failures to the {@link Problems} service.  It is a
 * Gradle managed type, and so it can serve as an injection point for any types wishing to be notified or respond
 * to the variant selection process.
 */
public class ResolutionFailureHandler {
    private static final String DEFAULT_MESSAGE_PREFIX = "Review the variant matching algorithm at ";
    private static final String AMBIGUOUS_VARIANTS_PREFIX = "Ambiguity errors are explained in more detail at ";
    private static final String INCOMPATIBLE_VARIANTS_PREFIX = "Incompatible variant errors are explained in more detail at ";
    private static final String NO_MATCHING_VARIANTS_PREFIX = "No matching variant errors are explained in more detail at ";

    @SuppressWarnings("FieldCanBeLocal")
    private final Problems problemsService;
    private final DocumentationRegistry documentationRegistry;

    public ResolutionFailureHandler(Problems problemsService, DocumentationRegistry documentationRegistry) {
        this.problemsService = problemsService;
        this.documentationRegistry = documentationRegistry;
    }

    private void addBasicResolution(AbstractVariantSelectionException exception) {
        exception.addResolution(DEFAULT_MESSAGE_PREFIX + documentationRegistry.getDocumentationFor("variant_attributes", "sec:abm_algorithm") + ".");
    }

    // region Artifact Variant Selection Failures
    public NoMatchingArtifactVariantsException noMatchingArtifactVariantFailure(AttributesSchema schema, String displayName, ImmutableAttributes componentRequested, List<? extends ResolvedVariant> variants, AttributeMatcher matcher, AttributeDescriber attributeDescriber) {
        String message = buildNoMatchingVariantsFailureMsg(displayName, componentRequested, variants, matcher, attributeDescriber);
        NoMatchingArtifactVariantsException e = new NoMatchingArtifactVariantsException(message);
        addBasicResolution(e);
        return e;
    }

    public AmbiguousArtifactVariantsException ambiguousArtifactVariantsFailure(AttributesSchema schema, String displayName, ImmutableAttributes componentRequested, List<? extends ResolvedVariant> matches, AttributeMatcher matcher, Set<ResolvedVariant> discarded, AttributeDescriber attributeDescriber) {
        String message = buildMultipleMatchingVariantsFailureMsg(attributeDescriber, displayName, componentRequested, matches, matcher, discarded);
        AmbiguousArtifactVariantsException e = new AmbiguousArtifactVariantsException(message);
        addBasicResolution(e);
        return e;
    }

    public AmbiguousArtifactTransformException ambiguousArtifactTransformationFailure(AttributesSchema schema, String displayName, ImmutableAttributes componentRequested, List<TransformedVariant> transformedVariants) {
        String message = buildAmbiguousTransformMsg(displayName, componentRequested, transformedVariants);
        AmbiguousArtifactTransformException e = new AmbiguousArtifactTransformException(message);
        addBasicResolution(e);
        return e;
    }

    // TODO: Unify this failure in the exception hierarchy with the others
    public BrokenResolvedArtifactSet unknownArtifactVariantSelectionFailure(AttributesSchema schema, ArtifactVariantSelectionException t) {
        BrokenResolvedArtifactSet e = new BrokenResolvedArtifactSet(t);
        // TODO: unify this with the other failures in the hierarchy so that basic resolution can be added
        //addBasicResolution(e);
        return e;
    }

    public BrokenResolvedArtifactSet unknownArtifactVariantSelectionFailure(AttributesSchema schema, ResolvedVariantSet producer, Exception cause) {
        String message = buildUnknownArtifactVariantFailureMsg(producer);
        ArtifactVariantSelectionException e = new ArtifactVariantSelectionException(message, cause);
        addBasicResolution(e);
        return unknownArtifactVariantSelectionFailure(schema, e);
    }

    public IncompatibleArtifactVariantsException incompatibleArtifactVariantsFailure(ComponentState selected, Set<NodeState> incompatibleNodes) {
        String message = buildIncompatibleArtifactVariantsFailureMsg(selected, incompatibleNodes);
        IncompatibleArtifactVariantsException e = new IncompatibleArtifactVariantsException(message);
        addBasicResolution(e);
        return e;
    }

    private String buildUnknownArtifactVariantFailureMsg(ResolvedVariantSet producer) {
        return String.format("Could not select a variant of %s that matches the consumer attributes.", producer.asDescribable().getDisplayName());
    }

    private String buildNoMatchingVariantsFailureMsg(
        String producerDisplayName,
        AttributeContainerInternal consumer,
        Collection<? extends ResolvedVariant> candidates,
        AttributeMatcher matcher, AttributeDescriber describer
    ) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("No variants of " + style(StyledTextOutput.Style.Info, producerDisplayName) + " match the consumer attributes");
        formatter.startChildren();
        for (ResolvedVariant variant : candidates) {
            formatter.node(variant.asDescribable().getCapitalizedDisplayName());
            formatAttributeMatchesForIncompatibility(formatter, consumer.asImmutable(), matcher, variant.getAttributes().asImmutable(), describer);
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private String buildMultipleMatchingVariantsFailureMsg(AttributeDescriber describer, String producerDisplayName, AttributeContainerInternal consumer, List<? extends ResolvedVariant> variants, AttributeMatcher matcher, Set<ResolvedVariant> discarded) {
        TreeFormatter formatter = new TreeFormatter();
        if (consumer.getAttributes().isEmpty()) {
            formatter.node("More than one variant of " + producerDisplayName + " matches the consumer attributes");
        } else {
            formatter.node("The consumer was configured to find " + describer.describeAttributeSet(consumer.asMap()) + ". However we cannot choose between the following variants of " + producerDisplayName);
        }
        formatter.startChildren();
        for (ResolvedVariant variant : variants) {
            formatter.node(variant.asDescribable().getCapitalizedDisplayName());
            formatAttributeMatchesForAmbiguity(formatter, consumer.asImmutable(), matcher, variant.getAttributes().asImmutable(), describer);
        }
        formatter.endChildren();
        if (!discarded.isEmpty()) {
            formatter.node("The following variants were also considered but didn't match the requested attributes:");
            formatter.startChildren();
            discarded.stream()
                .sorted(Comparator.comparing(v -> v.asDescribable().getCapitalizedDisplayName()))
                .forEach(discardedVariant -> {
                    formatter.node(discardedVariant.asDescribable().getCapitalizedDisplayName());
                    formatAttributeMatchesForIncompatibility(formatter, consumer.asImmutable(), matcher, discardedVariant.getAttributes().asImmutable(), describer);
                });
            formatter.endChildren();
        }
        return formatter.toString();
    }

    private String buildAmbiguousTransformMsg(String producerDisplayName, AttributeContainerInternal requested, List<TransformedVariant> candidates) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Found multiple transforms that can produce a variant of " + producerDisplayName + " with requested attributes");
        formatSortedAttributes(formatter, requested);
        formatter.node("Found the following transforms");

        Comparator<TransformedVariant> variantComparator =
            Comparator.<TransformedVariant, String>comparing(x -> x.getTransformChain().getDisplayName())
                .thenComparing(x -> x.getAttributes().toString());
        Map<ResolvedVariant, List<TransformedVariant>> variantToTransforms = candidates.stream().collect(Collectors.groupingBy(
            TransformedVariant::getRoot,
            () -> new TreeMap<>(Comparator.comparing(variant -> variant.asDescribable().getDisplayName())),
            Collectors.collectingAndThen(Collectors.toList(), list -> list.stream().sorted(variantComparator).collect(Collectors.toList()))));

        formatter.startChildren();
        for (Map.Entry<ResolvedVariant, List<TransformedVariant>> entry : variantToTransforms.entrySet()) {
            formatter.node("From '" + entry.getKey().asDescribable().getDisplayName() + "'");
            formatter.startChildren();
            formatter.node("With source attributes");
            formatSortedAttributes(formatter, entry.getKey().getAttributes());
            formatter.node("Candidate transform(s)");
            formatter.startChildren();
            for (TransformedVariant variant : entry.getValue()) {
                formatter.node("Transform '" + variant.getTransformChain().getDisplayName() + "' producing attributes:");
                formatSortedAttributes(formatter, variant.getAttributes());
            }
            formatter.endChildren();
            formatter.endChildren();
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private void formatSortedAttributes(TreeFormatter formatter, AttributeContainer attributes) {
        formatter.startChildren();
        for (Attribute<?> attribute : Ordering.usingToString().sortedCopy(attributes.keySet())) {
            formatter.node(attribute.getName() + " '" + attributes.getAttribute(attribute) + "'");
        }
        formatter.endChildren();
    }

    private String buildIncompatibleArtifactVariantsFailureMsg(ComponentState selected, Set<NodeState> incompatibleNodes) {
        StringBuilder sb = new StringBuilder("Multiple incompatible variants of ")
            .append(selected.getId())
            .append(" were selected:\n");
        ArrayList<NodeState> sorted = Lists.newArrayList(incompatibleNodes);
        sorted.sort(Comparator.comparing(NodeState::getNameWithVariant));
        for (NodeState node : sorted) {
            sb.append("   - Variant ").append(node.getNameWithVariant()).append(" has attributes ");
            formatAttributes(sb, node.getMetadata().getAttributes());
            sb.append("\n");
        }
        return sb.toString();
    }

    private void formatAttributes(StringBuilder sb, ImmutableAttributes attributes) {
        ImmutableSet<Attribute<?>> keySet = attributes.keySet();
        List<Attribute<?>> sorted = Lists.newArrayList(keySet);
        sorted.sort(Comparator.comparing(Attribute::getName));
        boolean space = false;
        sb.append("{");
        for (Attribute<?> attribute : sorted) {
            if (space) {
                sb.append(", ");
            }
            sb.append(attribute.getName()).append("=").append(attributes.getAttribute(attribute));
            space = true;
        }
        sb.append("}");
    }
    // endregion Artifact Variant Selection Failures

    // region Graph Variant Selection Failures
    public AmbiguousGraphVariantsException ambiguousGraphVariantsFailure(
        AttributeDescriber describer, AttributeContainerInternal fromConfigurationAttributes,
        AttributeMatcher attributeMatcher, List<? extends VariantGraphResolveState> matches,
        ComponentGraphResolveMetadata targetComponent, boolean variantAware,
        Set<VariantGraphResolveState> discarded
    ) {
        String message = buildAmbiguousGraphVariantsFailureMsg(new StyledDescriber(describer), fromConfigurationAttributes, attributeMatcher, matches, targetComponent, variantAware, discarded);
        AmbiguousGraphVariantsException e = new AmbiguousGraphVariantsException(message);
        e.addResolution(AMBIGUOUS_VARIANTS_PREFIX + documentationRegistry.getDocumentationFor("variant_model", "sub:variant-ambiguity" + "."));
        addBasicResolution(e);
        return e;
    }

    public IncompatibleGraphVariantsException incompatibleGraphVariantsFailure(
        AttributeContainerInternal fromConfigurationAttributes,
        AttributeMatcher attributeMatcher,
        ComponentGraphResolveMetadata targetComponent,
        ConfigurationGraphResolveState targetConfiguration,
        boolean variantAware,
        AttributeDescriber describer
    ) {
        String message = buildIncompatibleGraphVariantsFailureMsg(fromConfigurationAttributes, attributeMatcher, targetComponent, targetConfiguration, variantAware, describer);
        IncompatibleGraphVariantsException e = new IncompatibleGraphVariantsException(message);
        e.addResolution(INCOMPATIBLE_VARIANTS_PREFIX + documentationRegistry.getDocumentationFor("variant_model", "sub:variant-incompatible") + ".");
        addBasicResolution(e);
        return e;
    }

    public NoMatchingGraphVariantsException noMatchingGraphVariantFailure(
        AttributeDescriber describer,
        AttributeContainerInternal fromConfigurationAttributes,
        AttributeMatcher attributeMatcher,
        ComponentGraphResolveMetadata targetComponent,
        GraphSelectionCandidates candidates
    ) {
        String message = buildNoMatchingGraphVariantSelectionFailureMsg(new StyledDescriber(describer), fromConfigurationAttributes, attributeMatcher, targetComponent, candidates);
        NoMatchingGraphVariantsException e = new NoMatchingGraphVariantsException(message);
        addBasicResolution(e);
        e.addResolution(NO_MATCHING_VARIANTS_PREFIX + documentationRegistry.getDocumentationFor("variant_model", "sub:variant-no-match" + "."));
        return e;
    }

    public NoMatchingCapabilitiesException noMatchingCapabilitiesFailure(ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> requestedCapabilities, List<? extends VariantGraphResolveState> candidates) {
        String message = buildNoMatchingCapabilitiesFailureMsg(targetComponent, requestedCapabilities, candidates);
        NoMatchingCapabilitiesException e = new NoMatchingCapabilitiesException(message);
        addBasicResolution(e);
        return e;
    }

    public ConfigurationNotFoundException configurationNotFoundFailure(String targetConfigurationName, ComponentIdentifier targetComponentId) {
        String message = buildConfigurationNotFoundFailureMsg(targetConfigurationName, targetComponentId);
        ConfigurationNotFoundException e = new ConfigurationNotFoundException(message);
        addBasicResolution(e);
        return e;
    }

    public ExternalConfigurationNotFoundException externalConfigurationNotFoundFailure(ComponentIdentifier fromComponent, String fromConfiguration, String toConfiguration, ComponentIdentifier toComponent) {
        String message = buildExternalConfigurationNotFoundFailureMsg(fromComponent, fromConfiguration, toConfiguration, toComponent);
        ExternalConfigurationNotFoundException e = new ExternalConfigurationNotFoundException(message);
        addBasicResolution(e);
        return e;
    }

    public ConfigurationNotConsumableException configurationNotConsumableFailure(String targetComponentName, String targetConfigurationName) {
        String message = buildConfigurationNotConsumableFailureMsg(targetComponentName, targetConfigurationName);
        ConfigurationNotConsumableException e = new ConfigurationNotConsumableException(message);
        addBasicResolution(e);
        return e;
    }

    private String buildConfigurationNotFoundFailureMsg(String targetConfigurationName, ComponentIdentifier targetComponentId) {
        return String.format("A dependency was declared on configuration '%s' which is not declared in the descriptor for %s.", targetConfigurationName, targetComponentId.getDisplayName());
    }

    private String buildExternalConfigurationNotFoundFailureMsg(ComponentIdentifier fromComponent, String fromConfiguration, String toConfiguration, ComponentIdentifier toComponent) {
        return String.format("%s declares a dependency from configuration '%s' to configuration '%s' which is not declared in the descriptor for %s.", StringUtils.capitalize(fromComponent.getDisplayName()), fromConfiguration, toConfiguration, toComponent.getDisplayName());
    }

    private String buildConfigurationNotConsumableFailureMsg(String targetComponentName, String targetConfigurationName) {
        return String.format("Selected configuration '" + targetConfigurationName + "' on '" + targetComponentName + "' but it can't be used as a project dependency because it isn't intended for consumption by other components.");
    }

    private String buildAmbiguousGraphVariantsFailureMsg(
        AttributeDescriber describer, AttributeContainerInternal fromConfigurationAttributes,
        AttributeMatcher attributeMatcher, List<? extends VariantGraphResolveState> matches,
        ComponentGraphResolveMetadata targetComponent, boolean variantAware,
        Set<VariantGraphResolveState> discarded
    ) {
        Map<String, VariantGraphResolveState> ambiguousVariants = new TreeMap<>();
        for (VariantGraphResolveState match : matches) {
            ambiguousVariants.put(match.getName(), match);
        }
        TreeFormatter formatter = new TreeFormatter();
        String configTerm = variantAware ? "variants" : "configurations";
        if (fromConfigurationAttributes.isEmpty()) {
            formatter.node("Cannot choose between the following " + configTerm + " of ");
        } else {
            formatter.node("The consumer was configured to find " + describer.describeAttributeSet(fromConfigurationAttributes.asMap()) + ". However we cannot choose between the following " + configTerm + " of ");
        }
        formatter.append(style(StyledTextOutput.Style.Info, targetComponent.getId().getDisplayName()));
        formatter.startChildren();
        for (String configuration : ambiguousVariants.keySet()) {
            formatter.node(configuration);
        }
        formatter.endChildren();
        formatter.node("All of them match the consumer attributes");
        // We're sorting the names of the variants and later attributes
        // to make sure the output is consistently the same between invocations
        formatter.startChildren();
        for (VariantGraphResolveState ambiguousVariant : ambiguousVariants.values()) {
            formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, ambiguousVariant.getMetadata(), variantAware, true, describer);
        }
        formatter.endChildren();
        if (!discarded.isEmpty()) {
            formatter.node("The following " + configTerm + " were also considered but didn't match the requested attributes:");
            formatter.startChildren();
            discarded.stream()
                .sorted(Comparator.comparing(VariantGraphResolveState::getName))
                .forEach(discardedConf -> formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, discardedConf.getMetadata(), variantAware, false, describer));
            formatter.endChildren();
        }

        return formatter.toString();
    }

    private String buildIncompatibleGraphVariantsFailureMsg(
        AttributeContainerInternal fromConfigurationAttributes,
        AttributeMatcher attributeMatcher,
        ComponentGraphResolveMetadata targetComponent,
        ConfigurationGraphResolveState targetConfiguration,
        boolean variantAware,
        AttributeDescriber describer
    ) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Configuration '" + targetConfiguration.getName() + "' in " + style(StyledTextOutput.Style.Info, targetComponent.getId().getDisplayName()) + " does not match the consumer attributes");
        formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, targetConfiguration.asVariant().getMetadata(), variantAware, false, describer);
        return formatter.toString();
    }

    private String buildNoMatchingGraphVariantSelectionFailureMsg(AttributeDescriber describer, AttributeContainerInternal fromConfigurationAttributes, AttributeMatcher attributeMatcher, final ComponentGraphResolveMetadata targetComponent, GraphSelectionCandidates candidates) {
        boolean variantAware = candidates.isUseVariants();
        Map<String, VariantGraphResolveMetadata> variants = new TreeMap<>();
        if (variantAware) {
            for (VariantGraphResolveState variant : candidates.getVariants()) {
                variants.put(variant.getName(), variant.getMetadata());
            }
        } else {
            for (ConfigurationGraphResolveMetadata configuration : candidates.getCandidateConfigurations()) {
                variants.put(configuration.getName(), configuration);
            }
        }
        TreeFormatter formatter = new TreeFormatter();
        String targetVariantText = style(StyledTextOutput.Style.Info, targetComponent.getId().getDisplayName());
        if (fromConfigurationAttributes.isEmpty()) {
            formatter.node("Unable to find a matching " + (variantAware ? "variant" : "configuration") + " of " + targetVariantText);
        } else {
            formatter.node("No matching " + (variantAware ? "variant" : "configuration") + " of " + targetVariantText + " was found. The consumer was configured to find " + describer.describeAttributeSet(fromConfigurationAttributes.asMap()) + " but:");
        }
        formatter.startChildren();
        if (variants.isEmpty()) {
            formatter.node("None of the " + (variantAware ? "variants" : "consumable configurations") + " have attributes.");
        } else {
            // We're sorting the names of the configurations and later attributes
            // to make sure the output is consistently the same between invocations
            for (VariantGraphResolveMetadata variant : variants.values()) {
                formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, variant, variantAware, false, describer);
            }
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private String buildNoMatchingCapabilitiesFailureMsg(ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> requestedCapabilities, List<? extends VariantGraphResolveState> candidates) {
        StringBuilder sb = new StringBuilder("Unable to find a variant of ");
        sb.append(targetComponent.getId()).append(" providing the requested ");
        sb.append(CapabilitiesSupport.prettifyCapabilities(targetComponent, requestedCapabilities));
        sb.append(":\n");
        for (VariantGraphResolveState candidate : candidates) {
            sb.append("   - Variant ").append(candidate.getName()).append(" provides ");
            sb.append(CapabilitiesSupport.sortedCapabilityList(targetComponent, candidate.getCapabilities().getCapabilities())).append("\n");
        }
        return sb.toString();
    }

    private void formatConfiguration(
        TreeFormatter formatter,
        ComponentGraphResolveMetadata targetComponent,
        AttributeContainerInternal consumerAttributes,
        AttributeMatcher attributeMatcher,
        VariantGraphResolveMetadata variant,
        boolean variantAware,
        boolean ambiguous,
        AttributeDescriber describer
    ) {
        AttributeContainerInternal producerAttributes = variant.getAttributes();
        if (variantAware) {
            formatter.node("Variant '");
        } else {
            formatter.node("Configuration '");
        }
        formatter.append(variant.getName());
        formatter.append("'");
        if (variantAware) {
            formatter.append(" " + CapabilitiesSupport.prettifyCapabilities(targetComponent, variant.getCapabilities().getCapabilities()));
        }
        if (ambiguous) {
            formatAttributeMatchesForAmbiguity(formatter, consumerAttributes.asImmutable(), attributeMatcher, producerAttributes.asImmutable(), describer);
        } else {
            formatAttributeMatchesForIncompatibility(formatter, consumerAttributes.asImmutable(), attributeMatcher, producerAttributes.asImmutable(), describer);
        }
    }

    private void formatAttributeMatchesForIncompatibility(
        TreeFormatter formatter,
        ImmutableAttributes immutableConsumer,
        AttributeMatcher attributeMatcher,
        ImmutableAttributes immutableProducer,
        AttributeDescriber describer
    ) {
        Map<String, Attribute<?>> allAttributes = collectAttributes(immutableConsumer, immutableProducer);
        List<String> otherValues = Lists.newArrayListWithExpectedSize(allAttributes.size());
        Map<Attribute<?>, ?> compatibleAttrs = Maps.newLinkedHashMap();
        Map<Attribute<?>, ?> incompatibleAttrs = Maps.newLinkedHashMap();
        Map<Attribute<?>, ?> incompatibleConsumerAttrs = Maps.newLinkedHashMap();
        for (Attribute<?> attribute : allAttributes.values()) {
            Attribute<Object> untyped = Cast.uncheckedCast(attribute);
            String attributeName = attribute.getName();
            AttributeValue<Object> consumerValue = immutableConsumer.findEntry(untyped);
            AttributeValue<?> producerValue = immutableProducer.findEntry(attributeName);
            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.coerce(attribute), consumerValue.coerce(attribute))) {
                    compatibleAttrs.put(attribute, Cast.uncheckedCast(producerValue.get()));
                } else {
                    incompatibleAttrs.put(attribute, Cast.uncheckedCast(producerValue.get()));
                    incompatibleConsumerAttrs.put(attribute, Cast.uncheckedCast(consumerValue.get()));
                }
            } else if (consumerValue.isPresent()) {
                otherValues.add("Doesn't say anything about " + describer.describeMissingAttribute(attribute, consumerValue.get()));
            }
        }
        if (!compatibleAttrs.isEmpty()) {
            formatter.append(" declares ").append(style(StyledTextOutput.Style.SuccessHeader, describer.describeAttributeSet(compatibleAttrs)));
        }
        formatter.startChildren();
        if (!incompatibleAttrs.isEmpty()) {
            formatter.node("Incompatible because this component declares " + style(StyledTextOutput.Style.FailureHeader, describer.describeAttributeSet(incompatibleAttrs)) + " and the consumer needed <FailureHeader>" + describer.describeAttributeSet(incompatibleConsumerAttrs) + "</FailureHeader>");
        }
        formatAttributeSection(formatter, "Other compatible attribute", otherValues);
        formatter.endChildren();
    }

    private void formatAttributeMatchesForAmbiguity(
        TreeFormatter formatter,
        ImmutableAttributes immutableConsumer,
        AttributeMatcher attributeMatcher,
        ImmutableAttributes immutableProducer,
        AttributeDescriber describer
    ) {
        Map<String, Attribute<?>> allAttributes = collectAttributes(immutableConsumer, immutableProducer);
        Map<Attribute<?>, ?> compatibleAttrs = Maps.newLinkedHashMap();
        List<String> otherValues = Lists.newArrayListWithExpectedSize(allAttributes.size());
        for (Attribute<?> attribute : allAttributes.values()) {
            Attribute<Object> untyped = Cast.uncheckedCast(attribute);
            String attributeName = attribute.getName();
            AttributeValue<Object> consumerValue = immutableConsumer.findEntry(untyped);
            AttributeValue<?> producerValue = immutableProducer.findEntry(attributeName);
            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.coerce(attribute), consumerValue.coerce(attribute))) {
                    compatibleAttrs.put(attribute, Cast.uncheckedCast(producerValue.get()));
                }
            } else if (consumerValue.isPresent()) {
                otherValues.add("Doesn't say anything about " + describer.describeMissingAttribute(attribute, consumerValue.get()));
            } else {
                otherValues.add("Provides " + describer.describeExtraAttribute(attribute, producerValue.get()) + " but the consumer didn't ask for it");
            }
        }
        if (!compatibleAttrs.isEmpty()) {
            formatter.append(" declares ").append(style(StyledTextOutput.Style.SuccessHeader, describer.describeAttributeSet(compatibleAttrs)));
        }
        formatter.startChildren();
        formatAttributeSection(formatter, "Unmatched attribute", otherValues);
        formatter.endChildren();
    }

    private Map<String, Attribute<?>> collectAttributes(ImmutableAttributes consumerAttributes, ImmutableAttributes producerAttributes) {
        Map<String, Attribute<?>> allAttributes = new TreeMap<>();
        for (Attribute<?> attribute : producerAttributes.keySet()) {
            allAttributes.put(attribute.getName(), attribute);
        }
        for (Attribute<?> attribute : consumerAttributes.keySet()) {
            allAttributes.put(attribute.getName(), attribute);
        }
        return allAttributes;
    }

    private void formatAttributeSection(TreeFormatter formatter, String section, List<String> values) {
        if (!values.isEmpty()) {
            if (values.size() > 1) {
                formatter.node(section + "s");
            } else {
                formatter.node(section);
            }
            formatter.startChildren();
            values.forEach(formatter::node);
            formatter.endChildren();
        }
    }
    // endregion Graph Variant Selection Failures
}
