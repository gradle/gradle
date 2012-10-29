/*
 * Copyright 2012 the original author or authors.
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


import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.tasks.CommandLineOption
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.diagnostics.internal.GraphRenderer
import org.gradle.api.tasks.diagnostics.internal.dsl.DependencyResultSpecNotationParser
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

import javax.inject.Inject

import static org.gradle.logging.StyledTextOutput.Style.Info

/**
 * Generates a report that attempts to answer questions like:
 * <ul>
 *   <li>Why this dependency is in the dependency graph? What pulls this dependency into the graph?</li>
 *   <li>What are all the requested versions of this dependency?</li>
 *   <li>Why the dependency has this particular version selected?
 *   Is it because of the conflict resolution or perhaps this version was forced?</li>
 * </ul>
 *
 * Use this task to get insight into a particular dependency (or dependencies)
 * and find out what exactly happens during dependency resolution and conflict resolution.
 * If the dependency version was forced or selected by the conflict resolution
 * this information will be available in the report.
 * <p>
 * Compared to the gradle dependencies report ({@link DependencyReportTask}),
 * this report shows an 'inverted' dependency tree.
 * This means that the root of the tree is the dependency that matches the input specified by the user.
 * Then, the tree recurses into the dependents.
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

    //TODO SF create a group in the dsl reference for the help tasks. Document the dependencies report better if necessary.

    /**
     * Configuration to look the dependency in
     */
    Configuration configuration;

    /**
     * Selects the dependency (or dependencies if multiple matches found) to show the report for.
     */
    Spec<DependencyResult> dependencySpec;

    private final StyledTextOutput output;
    private final GraphRenderer renderer;

    @Inject
    DependencyInsightReportTask(StyledTextOutputFactory outputFactory) {
        output = outputFactory.create(getClass());
        renderer = new GraphRenderer(output);
    }

    /**
     * The dependency spec selects the dependency (or dependencies if multiple matches found) to show the report for.
     * The spec receives an instance of {@link DependencyResult} as parameter.
     *
     * @param dependencySpec
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
     *
     * @param dependencyInsightNotation
     */
    @CommandLineOption(options = "dependency", description = "Shows the details of given dependency.")
    public void setDependencySpec(Object dependencyInsightNotation) {
        def parser = DependencyResultSpecNotationParser.create()
        this.dependencySpec = parser.parseNotation(dependencyInsightNotation)
    }

    /**
     * Sets the configuration to look the dependency in.
     *
     * @param configuration
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Sets the configuration (via name) to look the dependency in.
     * <p>
     * This method is exposed to the command line interface. Example usage:
     * <pre>gradle dependencyInsight --configuration runtime --dependency slf4j</pre>
     *
     * @param configurationName
     */
    @CommandLineOption(options = "configuration", description = "Looks for the dependency in given configuration.")
    public void setConfiguration(String configurationName) {
        this.configuration = project.configurations.getByName(configurationName)
    }

    @TaskAction
    public void report() {
        if (configuration == null) {
            throw new ReportException("Dependency insight report cannot be generated because the input configuration was not specified. "
                    + "\nIt can be specified from the command line, e.g: 'dependencyInsight --configuration someConf --dependency someDep'")
        }
        if (dependencySpec == null) {
            throw new ReportException("Dependency insight report cannot be generated because the dependency to show was not specified."
                    + "\nIt can be specified from the command line, e.g: 'dependencyInsight --dependency someDep'")
        }

        ResolutionResult result = configuration.getIncoming().getResolutionResult();

        Set<DependencyResult> selectedDependencies = new LinkedHashSet<DependencyResult>()
        result.allDependencies { DependencyResult it ->
            //TODO SF revisit when developing unresolved dependencies story
            if (it instanceof ResolvedDependencyResult && dependencySpec.isSatisfiedBy(it)) {
                selectedDependencies << it
            }
        }

        if (selectedDependencies.empty) {
            output.println("No resolved dependencies matching given input were found in $configuration")
            return
        }

        def sortedDeps = new DependencyInsightReporter().prepare(selectedDependencies)

        def nodeRenderer = new NodeRenderer() {
            void renderNode(StyledTextOutput output, RenderableDependency node, Set<RenderableDependency> children, boolean alreadyRendered) {
                boolean leaf = children.empty
                output.text(leaf? DependencyInsightReportTask.this.configuration.name : node.name);
                if (alreadyRendered && !leaf) {
                    output.withStyle(Info).text(" (*)")
                }
            }
        }
        def dependencyGraphRenderer = new DependencyGraphRenderer(renderer, nodeRenderer)

        int i = 1
        for (RenderableDependency dependency: sortedDeps) {
            renderer.visit(new Action<StyledTextOutput>() {
                public void execute(StyledTextOutput out) {
                    out.withStyle(StyledTextOutput.Style.Identifier).text(dependency.name);
                    if (dependency.description) {
                        out.withStyle(StyledTextOutput.Style.Description).text(" (" + dependency.description + ")")
                    }
                }
            }, true);
            dependencyGraphRenderer.render(dependency)
            boolean last = i++ == sortedDeps.size()
            if (!last) {
                output.println()
            }
        }

        dependencyGraphRenderer.printLegend()
    }
}
