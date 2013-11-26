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

package org.gradle.api.tasks.diagnostics

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher
import org.gradle.api.internal.tasks.options.Option
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.diagnostics.internal.dsl.DependencyResultSpecNotationParser
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter
import org.gradle.internal.graph.GraphRenderer
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

import javax.inject.Inject

import static org.gradle.logging.StyledTextOutput.Style.*

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
    private final VersionMatcher versionMatcher;

    @Inject
    DependencyInsightReportTask(StyledTextOutputFactory outputFactory, VersionMatcher versionMatcher) {
        output = outputFactory.create(getClass());
        renderer = new GraphRenderer(output)
        this.versionMatcher = versionMatcher
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
    @Option(option = "dependency", description = "Shows the details of given dependency.")
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
    @Option(option = "configuration", description = "Looks for the dependency in given configuration.")
    public void setConfiguration(String configurationName) {
        this.configuration = project.configurations.getByName(configurationName)
    }

    @TaskAction
    public void report() {
        if (configuration == null) {
            throw new InvalidUserDataException("Dependency insight report cannot be generated because the input configuration was not specified. "
                    + "\nIt can be specified from the command line, e.g: '$path --configuration someConf --dependency someDep'")
        }
        if (dependencySpec == null) {
            throw new InvalidUserDataException("Dependency insight report cannot be generated because the dependency to show was not specified."
                    + "\nIt can be specified from the command line, e.g: '$path --dependency someDep'")
        }

        ResolutionResult result = configuration.getIncoming().getResolutionResult();

        Set<DependencyResult> selectedDependencies = new LinkedHashSet<DependencyResult>()
        result.allDependencies { DependencyResult it ->
            if (dependencySpec.isSatisfiedBy(it)) {
                selectedDependencies << it
            }
        }

        if (selectedDependencies.empty) {
            output.println("No dependencies matching given input were found in $configuration")
            return
        }

        def sortedDeps = new DependencyInsightReporter().prepare(selectedDependencies, versionMatcher)

        def nodeRenderer = new NodeRenderer() {
            void renderNode(StyledTextOutput output, RenderableDependency node, boolean alreadyRendered) {
                boolean leaf = node.children.empty
                output.text(leaf ? DependencyInsightReportTask.this.configuration.name : node.name);
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
                    out.withStyle(Identifier).text(dependency.name);
                    if (dependency.description) {
                        out.withStyle(Description).text(" ($dependency.description)")
                    }
                    if (!dependency.resolvable) {
                        out.withStyle(Failure).text(" FAILED")
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
