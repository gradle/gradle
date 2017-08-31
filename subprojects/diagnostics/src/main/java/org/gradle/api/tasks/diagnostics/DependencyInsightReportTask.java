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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.tasks.options.Option;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.dsl.DependencyResultSpecNotationConverter;
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.LegendRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.typeconversion.NotationParser;

import javax.inject.Inject;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.*;

/**
 * Generates a report that attempts to answer questions like:
 * <ul>
 *  <li>Why is this dependency in the dependency graph?</li>
 *  <li>Exactly which dependencies are pulling this dependency into the graph?</li>
 *  <li>What is the actual version (i.e. *selected* version) of the dependency that will be used? Is it the same as what was *requested*?</li>
 *  <li>Why is the *selected* version of a dependency different to the *requested*?</li>
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
@Incubating
public class DependencyInsightReportTask extends DefaultTask {

    private Configuration configuration;
    private Spec<DependencyResult> dependencySpec;

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

    @TaskAction
    public void report() {
        final Configuration configuration = getConfiguration();
        if (configuration == null) {
            throw new InvalidUserDataException("Dependency insight report cannot be generated because the input configuration was not specified. "
                    + "\nIt can be specified from the command line, e.g: '" + getPath() + " --configuration someConf --dependency someDep'");
        }

        if (dependencySpec == null) {
            throw new InvalidUserDataException("Dependency insight report cannot be generated because the dependency to show was not specified."
                    + "\nIt can be specified from the command line, e.g: '" + getPath() + " --dependency someDep'");
        }


        StyledTextOutput output = getTextOutputFactory().create(getClass());
        GraphRenderer renderer = new GraphRenderer(output);

        ResolutionResult result = configuration.getIncoming().getResolutionResult();

        final Set<DependencyResult> selectedDependencies = new LinkedHashSet<DependencyResult>();
        result.allDependencies(new Action<DependencyResult>() {
            @Override
            public void execute(DependencyResult dependencyResult) {
                if (dependencySpec.isSatisfiedBy(dependencyResult)) {
                    selectedDependencies.add(dependencyResult);
                }
            }
        });

        if (selectedDependencies.isEmpty()) {
            output.println("No dependencies matching given input were found in " + String.valueOf(configuration));
            return;
        }

        Collection<RenderableDependency> sortedDeps = new DependencyInsightReporter().prepare(selectedDependencies, getVersionSelectorScheme(), getVersionComparator());

        NodeRenderer nodeRenderer = new NodeRenderer() {
            public void renderNode(StyledTextOutput target, RenderableDependency node, boolean alreadyRendered) {
                boolean leaf = node.getChildren().isEmpty();
                target.text(leaf ? configuration.getName() : node.getName());
                if (alreadyRendered && !leaf) {
                    target.withStyle(Info).text(" (*)");
                }
            }
        };

        LegendRenderer legendRenderer = new LegendRenderer(output);
        DependencyGraphRenderer dependencyGraphRenderer = new DependencyGraphRenderer(renderer, nodeRenderer, legendRenderer);

        int i = 1;
        for (final RenderableDependency dependency : sortedDeps) {
            renderer.visit(new Action<StyledTextOutput>() {
                public void execute(StyledTextOutput out) {
                    out.withStyle(Identifier).text(dependency.getName());

                    if (StringUtils.isNotEmpty(dependency.getDescription())) {
                        out.withStyle(Description).text(" (" + dependency.getDescription() + ")");
                    }

                    switch (dependency.getResolutionState()) {
                        case FAILED:
                            out.withStyle(Failure).text(" FAILED");
                            break;
                        case RESOLVED:
                            break;
                        case UNRESOLVED:
                            out.withStyle(Failure).text(" (n)");
                            break;
                    }

                }

            }, true);
            dependencyGraphRenderer.render(dependency);
            boolean last = i++ == sortedDeps.size();
            if (!last) {
                output.println();
            }

        }


        legendRenderer.printLegend();
    }
}
