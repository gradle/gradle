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
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProvider;
import org.gradle.api.internal.artifacts.configurations.ResolvableDependenciesInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.resolver.ResolutionOutputsInternal;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
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
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Header;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Normal;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

/**
 * Generates a report that attempts to answer questions like:
 * <ul>
 * <li>Why is this dependency in the dependency graph?</li>
 * <li>Exactly which dependencies are pulling this dependency into the graph?</li>
 * <li>What is the actual version (i.e. *selected* version) of the dependency that will be used? Is it the same as what was *requested*?</li>
 * <li>Why is the *selected* version of a dependency different to the *requested*?</li>
 * <li>What variants are available for this dependency?</li>
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
 * For more information on how to configure those please refer to docs for {@link #setDependencySpec(Object)} and
 * {@link #setConfiguration(String)}.
 * <p>
 * The task can also be configured from the command line.
 * For more information please refer to {@link #setDependencySpec(Object)}, {@link #setConfiguration(String)},
 * {@link #setShowSinglePathToDependency(boolean)}, and {@link #getShowingAllVariants()}.
 */
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class DependencyInsightReportTask extends DefaultTask {

    private Spec<DependencyResult> dependencySpec;
    private boolean showSinglePathToDependency;
    private final Property<Boolean> showingAllVariants = getProject().getObjects().property(Boolean.class);
    private transient Configuration configuration;
    private final Property<ResolvedComponentResult> rootComponentProperty = getProject().getObjects().property(ResolvedComponentResult.class);

    // this field is named with a starting `z` to be serialized after `rootComponentProperty`
    // because the serialization of `rootComponentProperty` can still trigger callback that can affect
    // a value of `configuration.getAttributes()`.
    // TODO:configuration-cache find a way to clean up this #23732
    private Provider<AttributeContainer> zConfigurationAttributes;
    private ResolutionErrorRenderer errorHandler;
    private String configurationName;
    private String configurationDescription;

    /**
     * The root component of the dependency graph to be inspected.
     *
     * @since 7.5
     */
    @Input
    @Optional
    @Incubating
    public Property<ResolvedComponentResult> getRootComponentProperty() {
        // Required to maintain DslObject mapping
        Configuration configuration = getConfiguration();
        if (!rootComponentProperty.isPresent() && configuration != null && dependencySpec != null) {
            if (getShowingAllVariants().get()) {
                ConfigurationInternal configurationInternal = (ConfigurationInternal) configuration;
                if (!configurationInternal.isCanBeMutated()) {
                    throw new IllegalStateException(
                        "The configuration '" + configuration.getName() + "' is not mutable. " +
                        "In order to use the '--all-variants' option, the configuration must not be resolved before this task is executed."
                    );
                }
                configurationInternal.getResolutionStrategy().setIncludeAllSelectableVariantResults(true);
            }
            configurationName = configuration.getName();
            configurationDescription = configuration.toString();
            zConfigurationAttributes = getProject().provider(configuration::getAttributes);

            ProviderFactory providerFactory = getProject().getProviders();
            ResolutionOutputsInternal resolutionOutputs = ((ResolvableDependenciesInternal) configuration.getIncoming()).getResolutionOutputs();
            ResolutionResultProvider<VisitedGraphResults> graphResultsProvider = resolutionOutputs.getRawResults().map(ResolverResults::getVisitedGraph);
            errorHandler.addErrorSource(providerFactory.provider(() ->
                graphResultsProvider.getValue().getResolutionFailure()
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList()))
            );
            rootComponentProperty.set(providerFactory.provider(() -> {
                // We do not use the public resolution result API to avoid throwing exceptions that we visit above
                return graphResultsProvider.getValue().getResolutionResult().getRootSource().get();
            }));
        }
        return rootComponentProperty;
    }

    /**
     * Selects the dependency (or dependencies if multiple matches found) to show the report for.
     * @deprecated Not intended for public use.
     */
    @Internal
    @Deprecated
    public @Nullable Spec<DependencyResult> getDependencySpec() {
        DeprecationLogger
            .deprecateMethod(DependencyInsightReportTask.class, "getDependencySpec()")
            .withContext("This method is not intended for public use.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "dependency-insight-report-task-get-dependency-spec")
            .nagUser();
        return dependencySpec;
    }

    /**
     * The dependency spec selects the dependency (or dependencies if multiple matches found) to show the report for.
     * The spec receives an instance of {@link DependencyResult} as parameter.
     */
    public void setDependencySpec(@Nullable Spec<DependencyResult> dependencySpec) {
        this.dependencySpec = dependencySpec;
        this.errorHandler = new ResolutionErrorRenderer(dependencySpec);
    }

    /**
     * Configures the dependency to show the report for.
     * Multiple notation formats are supported: Strings, instances of {@link Spec}
     * and groovy closures. Spec and closure receive {@link DependencyResult} as parameter.
     * Examples of String notation: 'org.slf4j:slf4j-api', 'slf4j-api', or simply: 'slf4j'.
     * The input may potentially match multiple dependencies.
     * See also {@link #setDependencySpec(Spec)}
     * <p>
     * This method is exposed to the command line interface. Example usage:
     * <pre>gradle dependencyInsight --dependency slf4j</pre>
     */
    @Option(option = "dependency", description = "Shows the details of given dependency.")
    public void setDependencySpec(@Nullable Object dependencyInsightNotation) {
        NotationParser<Object, Spec<DependencyResult>> parser = DependencyResultSpecNotationConverter.parser();
        setDependencySpec(parser.parseNotation(dependencyInsightNotation));
    }

    /**
     * Configuration to look the dependency in
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public @Nullable Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the configuration to look the dependency in.
     */
    public void setConfiguration(@Nullable Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Sets the configuration (via name) to look the dependency in.
     * <p>
     * This method is exposed to the command line interface. Example usage:
     * <pre>gradle dependencyInsight --configuration runtime --dependency slf4j</pre>
     */
    @Option(option = "configuration", description = "Looks for the dependency in given configuration.")
    public void setConfiguration(@Nullable String configurationName) {
        setConfiguration(
            configurationName == null
                ? null
                : ConfigurationFinder.find(getProject().getConfigurations(), configurationName)
        );
    }

    /**
     * Tells if the report should only show one path to each dependency.
     *
     * @since 4.9
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public boolean isShowSinglePathToDependency() {
        return showSinglePathToDependency;
    }

    /**
     * Tells if the report should only display a single path to each dependency, which
     * can be useful when the graph is large. This is false by default, meaning that for
     * each dependency, the report will display all paths leading to it.
     *
     * <p>
     * This method is exposed to the command line interface. Example usage:
     * <pre>gradle dependencyInsight --single-path</pre>
     *
     * @since 4.9
     */
    @Option(option = "single-path", description = "Show at most one path to each dependency")
    public void setShowSinglePathToDependency(boolean showSinglePathToDependency) {
        this.showSinglePathToDependency = showSinglePathToDependency;
    }

    /**
     * Show all variants of each displayed dependency.
     *
     * <p>
     * Due to internal limitations, this option only works when the {@link #getConfiguration() configuration} is
     * unresolved before the execution of this task.
     * </p>
     *
     * <p>
     * This method is exposed to the command line interface. Example usage:
     * <pre>gradle dependencyInsight --all-variants</pre>
     *
     * @since 7.5
     */
    @Option(option = "all-variants", description = "Show all variants of each dependency")
    @Incubating
    @Internal
    public Property<Boolean> getShowingAllVariants() {
        return showingAllVariants;
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
     * An injected {@link AttributesFactory}.
     *
     * @since 4.9
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Inject
    protected AttributesFactory getImmutableAttributesFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * An injected {@link AttributesFactory}.
     * <p>
     * Previously named {@code getImmutableAttributesFactory}, this method has been renamed for better internal alignment.
     *
     * @since 8.12
     */
    @Internal
    @Incubating
    protected AttributesFactory getAttributesFactory() {
        return getImmutableAttributesFactory();
    }

    @TaskAction
    public void report() {
        assertValidTaskConfiguration();
        ResolvedComponentResult rootComponent = getRootComponentProperty().get();

        StyledTextOutput output = getTextOutputFactory().create(getClass());
        Set<DependencyResult> selectedDependencies = selectDependencies(rootComponent);

        if (selectedDependencies.isEmpty()) {
            output.println("No dependencies matching given input were found in " + configurationDescription);
            return;
        }
        errorHandler.renderErrors(output);
        renderSelectedDependencies(output, selectedDependencies);
        renderBuildScanHint(output);
    }

    private void renderSelectedDependencies(StyledTextOutput output, Set<DependencyResult> selectedDependencies) {
        GraphRenderer renderer = new GraphRenderer(output);
        DependencyInsightReporter reporter = new DependencyInsightReporter(getVersionSelectorScheme(), getVersionComparator(), getVersionParser());
        Collection<RenderableDependency> itemsToRender = reporter.convertToRenderableItems(selectedDependencies, isShowSinglePathToDependency());
        RootDependencyRenderer rootRenderer = new RootDependencyRenderer(this, zConfigurationAttributes.get(), getAttributesFactory());
        ReplaceProjectWithConfigurationNameRenderer dependenciesRenderer = new ReplaceProjectWithConfigurationNameRenderer(configurationName);
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

    private void assertValidTaskConfiguration() {
        if (configurationName == null) {
            throw new InvalidUserDataException("Dependency insight report cannot be generated because the input configuration was not specified. "
                + "\nIt can be specified from the command line, e.g: '" + getPath() + " --configuration someConf --dependency someDep'");
        }

        if (dependencySpec == null) {
            throw new InvalidUserDataException("Dependency insight report cannot be generated because the dependency to show was not specified."
                + "\nIt can be specified from the command line, e.g: '" + getPath() + " --dependency someDep'");
        }
    }

    private Set<DependencyResult> selectDependencies(ResolvedComponentResult rootComponent) {
        final Set<DependencyResult> selectedDependencies = new LinkedHashSet<>();
        eachDependency(rootComponent, dependencyResult -> {
            if (Objects.requireNonNull(dependencySpec).isSatisfiedBy(dependencyResult)) {
                selectedDependencies.add(dependencyResult);
            }
        }, new HashSet<>());
        return selectedDependencies;
    }

    private void eachDependency(
        ResolvedComponentResult node,
        Action<? super DependencyResult> dependencyAction,
        Set<ResolvedComponentResult> visited
    ) {
        if (!visited.add(node)) {
            return;
        }
        for (DependencyResult d : node.getDependencies()) {
            dependencyAction.execute(d);
            if (d instanceof ResolvedDependencyResult) {
                eachDependency(((ResolvedDependencyResult) d).getSelected(), dependencyAction, visited);
            }
        }
    }

    private AttributeMatchDetails match(Attribute<?> actualAttribute, @Nullable Object actualValue, AttributeContainer requestedAttributes) {
        for (Attribute<?> requested : requestedAttributes.keySet()) {
            Object requestedValue = requestedAttributes.getAttribute(requested);
            if (requested.getName().equals(actualAttribute.getName())) {
                // found an attribute with the same name, but they do not necessarily have the same type
                if (requested.equals(actualAttribute)) {
                    if (Objects.equals(actualValue, requestedValue)) {
                        return new AttributeMatchDetails(MatchType.EQUAL, requested, requestedValue);
                    }
                } else {
                    // maybe it matched through coercion
                    Object actualString = actualValue != null ? actualValue.toString() : null;
                    Object requestedString = requestedValue != null ? requestedValue.toString() : null;
                    if (Objects.equals(actualString, requestedString)) {
                        return new AttributeMatchDetails(MatchType.EQUAL, requested, requestedValue);
                    }
                }
                // TODO check for COMPATIBLE here, in a way compatible with configuration cache.
                // The branch ot/captchalogue/dependency-insights-compatibility-logging has the original code that isn't CC compatible.
                return new AttributeMatchDetails(MatchType.INCOMPATIBLE, requested, requestedValue);
            }
        }
        return new AttributeMatchDetails(MatchType.NOT_REQUESTED, null, null);
    }

    private static final class RootDependencyRenderer implements NodeRenderer {
        private final DependencyInsightReportTask task;
        private final AttributeContainer configurationAttributes;
        private final AttributesFactory attributesFactory;

        public RootDependencyRenderer(DependencyInsightReportTask task, AttributeContainer configurationAttributes, AttributesFactory attributesFactory) {
            this.task = task;
            this.configurationAttributes = configurationAttributes;
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
            if (task.getShowingAllVariants().get()) {
                out.style(Header);
                out.println();
                out.text("-------------------").println();
                out.text("Selected Variant(s)").println();
                out.text("-------------------");
                out.style(Normal);
                out.println();
            }
            for (ResolvedVariantResult variant : dependency.getResolvedVariants()) {
                printVariant(out, dependency, variant, true);
            }
            if (task.getShowingAllVariants().get()) {
                out.style(Header);
                out.println();
                out.println();
                out.text("---------------------").println();
                out.text("Unselected Variant(s)").println();
                out.text("---------------------");
                out.println();
                out.style(Normal);

                List<ResolvedVariantResult> sortedVariants = dependency.getAllVariants().stream()
                    .sorted(Comparator.comparing(ResolvedVariantResult::getDisplayName))
                    .collect(Collectors.toList());

                for (ResolvedVariantResult variant : sortedVariants) {
                    if (selectedVariantNames.contains(variant.getDisplayName())) {
                        continue;
                    }
                    // Currently, since the compatibility column is unusable, pass true for selected to prevent its output.
                    printVariant(out, dependency, variant, true);
                    out.println();
                }
            }
        }

        private void printVariant(
            StyledTextOutput out, RenderableDependency dependency, ResolvedVariantResult variant, boolean selected
        ) {
            AttributeContainer attributes = variant.getAttributes();
            AttributeContainer requested = getRequestedAttributes(dependency);
            AttributeBuckets buckets = bucketAttributes(attributes, requested);

            out.println().style(Normal).text("  Variant ");

            // For now, do not style -- see ot/captchalogue/dependency-insights-compatibility-logging for the original styling choices
            out.text(variant.getDisplayName()).style(Normal).text(":").println();
            if (!attributes.isEmpty() || !requested.isEmpty()) {
                writeAttributeBlock(out, attributes, requested, buckets, selected);
            }
        }

        private AttributeContainer getRequestedAttributes(RenderableDependency dependency) {
            if (dependency instanceof HasAttributes) {
                AttributeContainer dependencyAttributes = ((HasAttributes) dependency).getAttributes();
                return concat(configurationAttributes, dependencyAttributes);
            }
            return configurationAttributes;
        }

        private AttributeContainer concat(AttributeContainer configAttributes, AttributeContainer dependencyAttributes) {
            return attributesFactory.concat(
                ((AttributeContainerInternal) configAttributes).asImmutable(),
                ((AttributeContainerInternal) dependencyAttributes).asImmutable()
            );
        }

        private void writeAttributeBlock(
            StyledTextOutput out, AttributeContainer attributes, AttributeContainer requested,
            AttributeBuckets buckets, boolean selected
        ) {
            new StyledTable.Renderer().render(
                createAttributeTable(attributes, requested, buckets, selected),
                out
            );
        }

        private static final class AttributeBuckets {
            @SuppressWarnings("checkstyle:constantname")
            private static final Comparator<Attribute<?>> sortedByAttributeName = Comparator.comparing(Attribute::getName);

            Set<Attribute<?>> providedAttributes = new TreeSet<>(sortedByAttributeName);
            Map<Attribute<?>, AttributeMatchDetails> bothAttributes = new TreeMap<>(sortedByAttributeName);
            Set<Attribute<?>> requestedAttributes = new TreeSet<>(sortedByAttributeName);
        }

        private StyledTable createAttributeTable(
            AttributeContainer attributes, AttributeContainer requested, AttributeBuckets buckets, boolean selected
        ) {
            ImmutableList.Builder<String> header = ImmutableList.<String>builder()
                .add("Attribute Name", "Provided", "Requested");
            if (!selected) {
                header.add("Compatibility");
            }

            ImmutableList<StyledTable.Row> rows = buildRows(attributes, requested, buckets, selected);

            return new StyledTable(Strings.repeat(" ", 4), header.build(), rows);
        }

        private ImmutableList<StyledTable.Row> buildRows(
            AttributeContainer attributes, AttributeContainer requested, AttributeBuckets buckets, boolean selected
        ) {
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
            if (!selected) {
                text.add(match.matchType() == MatchType.INCOMPATIBLE ? "Incompatible" : "Compatible");
            }
            // For now, do not style -- see ot/captchalogue/dependency-insights-compatibility-logging for the original styling choices
            return new StyledTable.Row(text.build(), Normal);
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
    }

    private static class ReplaceProjectWithConfigurationNameRenderer implements NodeRenderer {
        private final String configurationName;

        public ReplaceProjectWithConfigurationNameRenderer(String configurationName) {
            this.configurationName = configurationName;
        }

        @Override
        public void renderNode(StyledTextOutput target, RenderableDependency node, boolean alreadyRendered) {
            boolean leaf = node.getChildren().isEmpty();
            target.text(leaf ? configurationName : node.getName());
            if (node.getDescription() != null) {
                target.text(" ").withStyle(Description).text(node.getDescription());
            }
            if (alreadyRendered && !leaf) {
                target.withStyle(Info).text(" (*)");
            }
        }
    }

}
