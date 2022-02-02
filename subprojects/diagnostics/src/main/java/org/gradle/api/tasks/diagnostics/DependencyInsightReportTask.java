/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.diagnostics;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.configurations.ResolvableDependenciesInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationFinder;
import org.gradle.api.tasks.diagnostics.internal.dependencies.AttributeMatchDetails;
import org.gradle.api.tasks.diagnostics.internal.dependencies.MatchType;
import org.gradle.api.tasks.diagnostics.internal.dsl.DependencyResultSpecNotationConverter;
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphsRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.Section;
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter;
import org.gradle.api.tasks.diagnostics.internal.text.StyledTable;
import org.gradle.api.tasks.options.Option;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Header;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Normal;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Success;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

/**
 * Generates a report that attempts to answer questions like:
 * <ul>
 * <li>Why is this dependency in the dependency graph?</li>
 * <li>Exactly which dependencies are pulling this dependency into the graph?</li>
 * <li>What is the actual version (i.e. *selected* version) of the dependency that will be used? Is it the same as what was *requested*?</li>
 * <li>Why is the *selected* version of a dependency different to the *requested*?</li>
 * </ul>
 *
 * Use this task to get insight into a particular dependency (or dependencies)
 * and find out what exactly happens during dependency resolution and conflict resolution.
 * If the dependency version was forced or selected by the conflict resolution
 * this information will be available in the report.
 * <p>
 * While the regular dependencies report ({@link DependencyReportTask}) shows the path from the top level dependencies down through the transitive dependencies,
 * the dependency insight report shows the path from a particular dependency to the dependencies that pulled it in.
 * That is, it is an inverted view of the regular dependencies report.
 * <p>
 * The task requires setting the dependency spec and the configuration.
 * For more information on how to configure those please refer to docs for
 * {@link DependencyInsightReportTask#setDependencySpec(Object)} and
 * {@link DependencyInsightReportTask#setConfiguration(String)}.
 * <p>
 * The task can also be configured from the command line.
 * For more information please refer to {@link DependencyInsightReportTask#setDependencySpec(Object)}
 * and {@link DependencyInsightReportTask#setConfiguration(String)}
 */
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public class DependencyInsightReportTask extends DefaultTask {

    private final Set<SelectionReasonOutput> enabledSelectionReasonOutputs = EnumSet.noneOf(SelectionReasonOutput.class);
    private Configuration configuration;
    private Spec<DependencyResult> dependencySpec;
    private boolean showSinglePathToDependency;
    private boolean showingAllVariants;

    /**
     * Selects the dependency (or dependencies if multiple matches found) to show the report for.
     */
    @Internal
    public Spec<DependencyResult> getDependencySpec() {
        return dependencySpec;
    }

    /**
     * The dependency spec selects the dependency (or dependencies if multiple matches found) to show the report for. The spec receives an instance of {@link DependencyResult} as parameter.
     */
    public void setDependencySpec(Spec<DependencyResult> dependencySpec) {
        this.dependencySpec = dependencySpec;
    }

    /**
     * Configures the dependency to show the report for.
     * Multiple notation formats are supported: Strings, instances of {@link Spec}
     * and groovy closures. Spec and closure receive {@link DependencyResult} as parameter.
     * Examples of String notation: 'org.slf4j:slf4j-api', 'slf4j-api', or simply: 'slf4j'.
     * The input may potentially match multiple dependencies.
     * See also {@link DependencyInsightReportTask#setDependencySpec(Spec)}
     * <p>
     * This method is exposed to the command line interface. Example usage:
     * <pre>gradle dependencyInsight --dependency slf4j</pre>
     */
    @Option(option = "dependency", description = "Shows the details of given dependency.")
    public void setDependencySpec(Object dependencyInsightNotation) {
        NotationParser<Object, Spec<DependencyResult>> parser = DependencyResultSpecNotationConverter.parser();
        this.dependencySpec = parser.parseNotation(dependencyInsightNotation);
    }

    /**
     * Configuration to look the dependency in
     */
    @Internal
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the configuration to look the dependency in.
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Sets the configuration (via name) to look the dependency in.
     * <p>
     * This method is exposed to the command line interface. Example usage:
     * <pre>gradle dependencyInsight --configuration runtime --dependency slf4j</pre>
     */
    @Option(option = "configuration", description = "Looks for the dependency in given configuration.")
    public void setConfiguration(String configurationName) {
        this.configuration = ConfigurationFinder.find(getProject().getConfigurations(), configurationName);
    }

    /**
     * Tells if the report should only show one path to each dependency.
     *
     * @since 4.9
     */
    @Internal
    public boolean isShowSinglePathToDependency() {
        return showSinglePathToDependency;
    }

    /**
     * Tells if the report should only display a single path to each dependency, which
     * can be useful when the graph is large. This is false by default, meaning that for
     * each dependency, the report will display all paths leading to it.
     *
     * @since 4.9
     */
    @Option(option = "single-path", description = "Show at most one path to each dependency")
    public void setShowSinglePathToDependency(boolean showSinglePathToDependency) {
        this.showSinglePathToDependency = showSinglePathToDependency;
    }

    @Internal
    public boolean isShowingAllVariants() {
        return showingAllVariants;
    }

    @Option(option = "all-variants", description = "Show all variants of each dependency")
    public void setShowingAllVariants(boolean showingAllVariants) {
        this.showingAllVariants = showingAllVariants;
    }

    @Internal
    public Set<SelectionReasonOutput> getEnabledSelectionReasonOutputs() {
        return enabledSelectionReasonOutputs;
    }

    @Option(option = "reasons", description = "Enable the given selection reason output")
    public void setEnabledSelectionReasonOutputs(List<SelectionReasonOutput> enabledSelectionReasonOutputs) {
        this.enabledSelectionReasonOutputs.clear();
        this.enabledSelectionReasonOutputs.addAll(enabledSelectionReasonOutputs);
    }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected VersionSelectorScheme getVersionSelectorScheme() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected VersionComparator getVersionComparator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected VersionParser getVersionParser() {
        throw new UnsupportedOperationException();
    }

    /**
     * An injected {@link ImmutableAttributesFactory}.
     *
     * @since 4.9
     */
    @Inject
    protected ImmutableAttributesFactory getAttributesFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void report() {
        final Configuration configuration = getConfiguration();
        assertValidTaskConfiguration(configuration);

        StyledTextOutput output = getTextOutputFactory().create(getClass());
        ResolutionErrorRenderer errorHandler = new ResolutionErrorRenderer(dependencySpec);
        Set<DependencyResult> selectedDependencies = selectDependencies(configuration, errorHandler);

        if (selectedDependencies.isEmpty()) {
            output.println("No dependencies matching given input were found in " + configuration);
            return;
        }
        errorHandler.renderErrors(output);
        renderSelectedDependencies(configuration, output, selectedDependencies);
        renderBuildScanHint(output);
    }

    private void renderSelectedDependencies(Configuration configuration, StyledTextOutput output, Set<DependencyResult> selectedDependencies) {
        GraphRenderer renderer = new GraphRenderer(output);
        boolean showDepSelectionReasons = getEnabledSelectionReasonOutputs().contains(SelectionReasonOutput.DEPENDENCY);
        DependencyInsightReporter reporter = new DependencyInsightReporter(getVersionSelectorScheme(), getVersionComparator(), getVersionParser(), showDepSelectionReasons);
        Collection<RenderableDependency> itemsToRender = reporter.convertToRenderableItems(selectedDependencies, isShowSinglePathToDependency());
        RootDependencyRenderer rootRenderer = new RootDependencyRenderer(this, configuration, getAttributesFactory());
        ReplaceProjectWithConfigurationNameRenderer dependenciesRenderer = new ReplaceProjectWithConfigurationNameRenderer(configuration);
        DependencyGraphsRenderer dependencyGraphRenderer = new DependencyGraphsRenderer(output, renderer, rootRenderer, dependenciesRenderer);
        dependencyGraphRenderer.setShowSinglePath(showSinglePathToDependency);
        dependencyGraphRenderer.render(itemsToRender);
        dependencyGraphRenderer.complete();
    }

    private void renderBuildScanHint(StyledTextOutput output) {
        output.println();
        output.text("A web-based, searchable dependency report is available by adding the ");
        output.withStyle(UserInput).format("--%s", StartParameterBuildOptions.BuildScanOption.LONG_OPTION);
        output.println(" option.");
    }

    private void assertValidTaskConfiguration(@Nullable Configuration configuration) {
        if (configuration == null) {
            throw new InvalidUserDataException("Dependency insight report cannot be generated because the input configuration was not specified. "
                    + "\nIt can be specified from the command line, e.g: '" + getPath() + " --configuration someConf --dependency someDep'");
        }

        if (dependencySpec == null) {
            throw new InvalidUserDataException("Dependency insight report cannot be generated because the dependency to show was not specified."
                    + "\nIt can be specified from the command line, e.g: '" + getPath() + " --dependency someDep'");
        }
    }

    private Set<DependencyResult> selectDependencies(Configuration configuration, ResolutionErrorRenderer errorHandler) {
        ResolvableDependenciesInternal incoming = (ResolvableDependenciesInternal) configuration.getIncoming();
        ResolutionResult result = incoming.getResolutionResult(errorHandler);

        final Set<DependencyResult> selectedDependencies = new LinkedHashSet<>();
        result.allDependencies(dependencyResult -> {
            if (dependencySpec.isSatisfiedBy(dependencyResult)) {
                selectedDependencies.add(dependencyResult);
            }
        });
        return selectedDependencies;
    }

    private AttributeMatchDetails match(Attribute<?> actualAttribute, @Nullable Object actualValue, AttributeContainer requestedAttributes) {
        for (Attribute<?> requested : requestedAttributes.keySet()) {
            Object requestedValue = requestedAttributes.getAttribute(requested);
            if (requested.getName().equals(actualAttribute.getName())) {
                // found an attribute with the same name, but they do not necessarily have the same type
                if (requested.equals(actualAttribute)) {
                    if (Objects.equals(actualValue, requestedValue)) {
                        return new AttributeMatchDetails(MatchType.REQUESTED, requested, requestedValue);
                    }
                } else {
                    // maybe it matched through coercion
                    Object actualString = actualValue != null ? actualValue.toString() : null;
                    Object requestedString = requestedValue != null ? requestedValue.toString() : null;
                    if (Objects.equals(actualString, requestedString)) {
                        return new AttributeMatchDetails(MatchType.REQUESTED, requested, requestedValue);
                    }
                }
                // TODO report "compatible" vs "incompatible" different values (MatchType.INCOMPATIBLE)
                return new AttributeMatchDetails(MatchType.DIFFERENT_VALUE, requested, requestedValue);
            }
        }
        return new AttributeMatchDetails(MatchType.NOT_REQUESTED, null, null);
    }

    private enum SelectionReasonOutput {
        DEPENDENCY,
        VARIANT,
    }

    private static final class RootDependencyRenderer implements NodeRenderer {
        private final DependencyInsightReportTask task;
        private final Configuration configuration;
        private final ImmutableAttributesFactory attributesFactory;

        public RootDependencyRenderer(DependencyInsightReportTask task, Configuration configuration, ImmutableAttributesFactory attributesFactory) {
            this.task = task;
            this.configuration = configuration;
            this.attributesFactory = attributesFactory;
        }

        @Override
        public void renderNode(StyledTextOutput out, RenderableDependency dependency, boolean alreadyRendered) {
            out.withStyle(Identifier).text(dependency.getName());
            if (StringUtils.isNotEmpty(dependency.getDescription())) {
                out.withStyle(Description).text(" (" + dependency.getDescription() + ")");
            }
            switch (dependency.getResolutionState()) {
                case FAILED:
                    out.withStyle(Failure).text(" FAILED");
                    break;
                case RESOLVED:
                case RESOLVED_CONSTRAINT:
                    break;
                case UNRESOLVED:
                    out.withStyle(Failure).text(" (n)");
                    break;
            }
            printVariantDetails(out, dependency);
            printExtraDetails(out, dependency);
        }

        private void printExtraDetails(StyledTextOutput out, RenderableDependency dependency) {
            List<Section> extraDetails = dependency.getExtraDetails();
            if (!extraDetails.isEmpty()) {
                printSections(out, extraDetails, 1);
            }
        }

        private void printSections(StyledTextOutput out, List<Section> extraDetails, int depth) {
            for (Section extraDetail : extraDetails) {
                printSection(out, extraDetail, depth);
                printSections(out, extraDetail.getChildren(), depth + 1);
            }
        }

        private void printSection(StyledTextOutput out, Section extraDetail, int depth) {
            out.println();
            String indent = StringUtils.leftPad("", 3 * depth) + (depth > 1 ? "- " : "");
            String appendix = extraDetail.getChildren().isEmpty() ? "" : ":";
            String description = StringUtils.trim(extraDetail.getDescription());
            String padding = "\n" + StringUtils.leftPad("", indent.length());
            description = description.replaceAll("(?m)(\r?\n)", padding);
            out.withStyle(Description).text(indent + description + appendix);
        }

        private void printVariantDetails(StyledTextOutput out, RenderableDependency dependency) {
            if (dependency.getResolvedVariants().isEmpty() && dependency.getAllVariants().isEmpty()) {
                return;
            }
            Set<String> selectedVariantNames = dependency.getResolvedVariants()
                .stream()
                .map(ResolvedVariantResult::getDisplayName)
                .collect(Collectors.toSet());
            if (task.isShowingAllVariants()) {
                out.style(Header);
                out.println();
                out.text("-------------------").println();
                out.text("Selected Variant(s)").println();
                out.text("-------------------");
                out.style(Normal);
            }
            for (ResolvedVariantResult variant : dependency.getResolvedVariants()) {
                printVariant(out, dependency, variant, true);
            }
            if (task.isShowingAllVariants()) {
                out.style(Header);
                out.text("---------------------").println();
                out.text("Unselected Variant(s)").println();
                out.text("---------------------");
                out.style(Normal);
                for (ResolvedVariantResult variant : dependency.getAllVariants()) {
                    if (selectedVariantNames.contains(variant.getDisplayName())) {
                        continue;
                    }
                    printVariant(out, dependency, variant, false);
                }
            }
        }

        private void printVariant(StyledTextOutput out, RenderableDependency dependency, ResolvedVariantResult variant, boolean selected) {
            out.println();
            out.withStyle(Description).text("Variant \"" + variant.getDisplayName() + "\":");
            out.println();
            AttributeContainer attributes = variant.getAttributes();
            AttributeContainer requested = getRequestedAttributes(configuration, dependency);
            if (!attributes.isEmpty() || !requested.isEmpty()) {
                writeAttributeBlock(out, attributes, requested, selected);
            }
        }

        private AttributeContainer getRequestedAttributes(Configuration configuration, RenderableDependency dependency) {
            if (dependency instanceof HasAttributes) {
                AttributeContainer dependencyAttributes = ((HasAttributes) dependency).getAttributes();
                return concat(configuration.getAttributes(), dependencyAttributes);
            }
            return configuration.getAttributes();
        }

        private AttributeContainer concat(AttributeContainer configAttributes, AttributeContainer dependencyAttributes) {
            return attributesFactory.concat(
                    ((AttributeContainerInternal) configAttributes).asImmutable(),
                    ((AttributeContainerInternal) dependencyAttributes).asImmutable());
        }

        private void writeAttributeBlock(StyledTextOutput out, AttributeContainer attributes, AttributeContainer requested, boolean selected) {
            out.withStyle(Description).text("  Attributes:");
            out.println();
            if (task.enabledSelectionReasonOutputs.contains(SelectionReasonOutput.VARIANT)) {
                createAttributeTable(attributes, requested, selected).print(out);
            } else {
                writeFoundAttributes(out, attributes);
            }
        }

        private static final class AttributeBuckets {
            List<Attribute<?>> providedAttributes = new ArrayList<>();
            Map<Attribute<?>, AttributeMatchDetails> bothAttributes = new LinkedHashMap<>();
            List<Attribute<?>> requestedAttributes = new ArrayList<>();
        }

        private StyledTable createAttributeTable(AttributeContainer attributes, AttributeContainer requested, boolean selected) {
            ImmutableList.Builder<String> header = ImmutableList. <String>builder()
                .add("Name", "Provided", "Requested");
            if (!selected) {
                header.add("Compatibility");
            }

            ImmutableList<StyledTable.Row> rows = buildRows(attributes, requested, selected);

            return new StyledTable(Strings.repeat(" ", 4), header.build(), rows);
        }

        private ImmutableList<StyledTable.Row> buildRows(AttributeContainer attributes, AttributeContainer requested, boolean selected) {
            AttributeBuckets buckets = bucketAttributes(attributes, requested);

            ImmutableList.Builder<StyledTable.Row> rows = ImmutableList.builder();
            for (Attribute<?> attribute : buckets.providedAttributes) {
                rows.add(createProvidedRow(attributes, selected, attribute));
            }
            for (Map.Entry<Attribute<?>, AttributeMatchDetails> entry : buckets.bothAttributes.entrySet()) {
                rows.add(createMatchBasedRow(attributes, selected, entry));
            }
            for (Attribute<?> attribute : buckets.requestedAttributes) {
                rows.add(createRequestedRow(requested, selected, attribute));
            }
            return rows.build();
        }

        private AttributeBuckets bucketAttributes(AttributeContainer attributes, AttributeContainer requested) {
            // Bucket attributes into three groups:
            // 1. Attributes that are only in the variant
            // 2. Attributes that are both in the variant and requested by the configuration
            // 3. Attributes that are only in the requested configuration
            AttributeBuckets buckets = new AttributeBuckets();
            for (Attribute<?> attribute : attributes.keySet()) {
                AttributeMatchDetails details = task.match(attribute, attributes.getAttribute(attribute), requested);
                if (details.matchType() != MatchType.NOT_REQUESTED) {
                    buckets.bothAttributes.put(attribute, details);
                } else {
                    buckets.providedAttributes.add(attribute);
                }
            }
            for (Attribute<?> attribute : requested.keySet()) {
                // If it's not in the matches, it's only in the requested attributes
                if (buckets.bothAttributes.values().stream().map(AttributeMatchDetails::requested).noneMatch(Predicate.isEqual(attribute))) {
                    buckets.requestedAttributes.add(attribute);
                }
            }
            return buckets;
        }

        private StyledTable.Row createProvidedRow(AttributeContainer attributes, boolean selected, Attribute<?> attribute) {
            Object providedValue = attributes.getAttribute(attribute);
            ImmutableList.Builder<String> text = ImmutableList.<String>builder()
                .add(
                    attribute.getName(),
                    providedValue == null ? "" : providedValue.toString(),
                    ""
                );
            if (!selected) {
                text.add("Compatible");
            }
            return new StyledTable.Row(text.build(), Info);
        }

        private StyledTable.Row createMatchBasedRow(AttributeContainer attributes, boolean selected, Map.Entry<Attribute<?>, AttributeMatchDetails> entry) {
            Object providedValue = attributes.getAttribute(entry.getKey());
            AttributeMatchDetails match = entry.getValue();
            ImmutableList.Builder<String> text = ImmutableList.<String>builder()
                .add(
                    entry.getKey().getName(),
                    providedValue == null ? "" : providedValue.toString(),
                    String.valueOf(entry.getValue().requestedValue())
                );
            StyledTextOutput.Style style;
            switch (match.matchType()) {
                case REQUESTED:
                    style = Success;
                    break;
                case DIFFERENT_VALUE:
                case NOT_REQUESTED:
                    style = Info;
                    break;
                case INCOMPATIBLE:
                    style = StyledTextOutput.Style.Error;
                    break;
                default:
                    throw new IllegalStateException("Unknown match type: " + match.matchType());
            }
            if (!selected) {
                text.add(match.matchType() == MatchType.INCOMPATIBLE ? "Incompatible" : "Compatible");
            }
            return new StyledTable.Row(text.build(), style);
        }

        private StyledTable.Row createRequestedRow(AttributeContainer requested, boolean selected, Attribute<?> attribute) {
            Object requestedValue = requested.getAttribute(attribute);
            ImmutableList.Builder<String> text = ImmutableList.<String>builder()
                .add(
                    attribute.getName(),
                    "",
                    String.valueOf(requestedValue)
                );
            if (!selected) {
                text.add("Compatible");
            }
            return new StyledTable.Row(text.build(), Info);
        }

        private void writeFoundAttributes(StyledTextOutput out, AttributeContainer attributes) {
            int maxAttributeLen = attributes.keySet().stream()
                .mapToInt(attr -> attr.getName().length())
                .max()
                .orElse(0);
            for (Attribute<?> attribute : attributes.keySet()) {
                Object actualValue = attributes.getAttribute(attribute);
                out.withStyle(Description).text(Strings.repeat(" ", 4) + StringUtils.rightPad(attribute.getName(), maxAttributeLen) + " = " + actualValue);
                out.println();
            }
        }

    }

    private static class ReplaceProjectWithConfigurationNameRenderer implements NodeRenderer {
        private final Configuration configuration;

        public ReplaceProjectWithConfigurationNameRenderer(Configuration configuration) {
            this.configuration = configuration;
        }

        @Override
        public void renderNode(StyledTextOutput target, RenderableDependency node, boolean alreadyRendered) {
            boolean leaf = node.getChildren().isEmpty();
            target.text(leaf ? configuration.getName() : node.getName());
            if (node.getDescription() != null) {
                target.text(" ").withStyle(Description).text(node.getDescription());
            }
            if (alreadyRendered && !leaf) {
                target.withStyle(Info).text(" (*)");
            }
        }
    }

}
