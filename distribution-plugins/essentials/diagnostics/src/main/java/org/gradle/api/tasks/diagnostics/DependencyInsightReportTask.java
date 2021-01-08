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

import com.google.common.collect.Sets;
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
import org.gradle.api.tasks.diagnostics.internal.dsl.DependencyResultSpecNotationConverter;
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphsRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.Section;
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter;
import org.gradle.api.tasks.options.Option;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;
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
public class DependencyInsightReportTask extends DefaultTask {

    private Configuration configuration;
    private Spec<DependencyResult> dependencySpec;
    private boolean showSinglePathToDependency;

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
        this.configuration = getProject().getConfigurations().getByName(configurationName);
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
    @Option(option = "singlepath", description = "Show at most one path to each dependency")
    public void setShowSinglePathToDependency(boolean showSinglePathToDependency) {
        this.showSinglePathToDependency = showSinglePathToDependency;
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
        DependencyInsightReporter reporter = new DependencyInsightReporter(getVersionSelectorScheme(), getVersionComparator(), getVersionParser());
        Collection<RenderableDependency> itemsToRender = reporter.convertToRenderableItems(selectedDependencies, isShowSinglePathToDependency());
        RootDependencyRenderer rootRenderer = new RootDependencyRenderer(configuration, getAttributesFactory());
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


    private static AttributeMatchDetails match(Attribute<?> actualAttribute, Object actualValue, AttributeContainer requestedAttributes) {
        for (Attribute<?> requested : requestedAttributes.keySet()) {
            Object requestedValue = requestedAttributes.getAttribute(requested);
            if (requested.getName().equals(actualAttribute.getName())) {
                // found an attribute with the same name, but they do not necessarily have the same type
                if (requested.equals(actualAttribute)) {
                    if (actualValue.equals(requestedValue)) {
                        return new AttributeMatchDetails(MatchType.requested, requested, requestedValue);
                    }
                } else {
                    // maybe it matched through coercion
                    if (actualValue.toString().equals(requestedValue.toString())) {
                        return new AttributeMatchDetails(MatchType.requested, requested, requestedValue);
                    }
                }
                return new AttributeMatchDetails(MatchType.different_value, requested, requestedValue);
            }
        }
        return new AttributeMatchDetails(MatchType.not_requested, null, null);
    }

    private static class AttributeMatchDetails {
        private final MatchType matchType;
        @Nullable
        private final Attribute<?> requested;
        @Nullable
        private final Object requestedValue;

        private AttributeMatchDetails(MatchType matchType, @Nullable Attribute<?> requested, @Nullable Object requestedValue) {
            this.matchType = matchType;
            this.requested = requested;
            this.requestedValue = requestedValue;
        }
    }

    private enum MatchType {
        requested,
        different_value,
        not_requested
    }

    private static class RootDependencyRenderer implements NodeRenderer {
        private final Configuration configuration;
        private final ImmutableAttributesFactory attributesFactory;

        public RootDependencyRenderer(Configuration configuration, ImmutableAttributesFactory attributesFactory) {
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
            List<ResolvedVariantResult> resolvedVariants = dependency.getResolvedVariants();
            for (ResolvedVariantResult resolvedVariant : resolvedVariants) {
                out.println();
                out.withStyle(Description).text("   variant \"" + resolvedVariant.getDisplayName() + "\"");
                AttributeContainer attributes = resolvedVariant.getAttributes();
                AttributeContainer requested = getRequestedAttributes(configuration, dependency);
                if (!attributes.isEmpty() || !requested.isEmpty()) {
                    writeAttributeBlock(out, attributes, requested);
                }
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

        private void writeAttributeBlock(StyledTextOutput out, AttributeContainer attributes, AttributeContainer requested) {
            out.withStyle(Description).text(" [");
            out.println();
            int maxAttributeLen = computeAttributePadding(attributes, requested);
            Set<Attribute<?>> matchedAttributes = Sets.newLinkedHashSet();
            writeFoundAttributes(out, attributes, requested, maxAttributeLen, matchedAttributes);
            Sets.SetView<Attribute<?>> missing = Sets.difference(requested.keySet(), matchedAttributes);
            if (!missing.isEmpty()) {
                writeMissingAttributes(out, requested, maxAttributeLen, missing);
            }
            out.withStyle(Description).text("   ]");
        }

        private void writeMissingAttributes(StyledTextOutput out, AttributeContainer requested, int maxAttributeLen, Sets.SetView<Attribute<?>> missing) {
            if (missing.size() != requested.keySet().size()) {
                out.println();
            }
            out.withStyle(Description).text("      Requested attributes not found in the selected variant:");
            out.println();
            for (Attribute<?> missingAttribute : missing) {
                out.withStyle(Description).text("         " + StringUtils.rightPad(missingAttribute.getName(), maxAttributeLen) + " = " + requested.getAttribute(missingAttribute));
                out.println();
            }
        }

        private void writeFoundAttributes(StyledTextOutput out, AttributeContainer attributes, AttributeContainer requested, int maxAttributeLen, Set<Attribute<?>> matchedAttributes) {
            for (Attribute<?> attribute : attributes.keySet()) {
                Object actualValue = attributes.getAttribute(attribute);
                AttributeMatchDetails match = match(attribute, actualValue, requested);
                out.withStyle(Description).text("      " + StringUtils.rightPad(attribute.getName(), maxAttributeLen) + " = " + actualValue);
                Attribute<?> requestedAttribute = match.requested;
                if (requestedAttribute != null) {
                    matchedAttributes.add(requestedAttribute);
                }
                switch (match.matchType) {
                    case requested:
                        break;
                    case different_value:
                        out.withStyle(Info).text(" (compatible with: " + match.requestedValue + ")");
                        break;
                    case not_requested:
                        out.withStyle(Info).text(" (not requested)");
                        break;
                }
                out.println();
            }
        }

        private int computeAttributePadding(AttributeContainer attributes, AttributeContainer requested) {
            int maxAttributeLen = 0;
            for (Attribute<?> attribute : requested.keySet()) {
                maxAttributeLen = Math.max(maxAttributeLen, attribute.getName().length());
            }
            for (Attribute<?> attribute : attributes.keySet()) {
                maxAttributeLen = Math.max(maxAttributeLen, attribute.getName().length());
            }
            return maxAttributeLen;
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
