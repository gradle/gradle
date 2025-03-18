/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.reporting.dependencies;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.reporting.dependencies.internal.DefaultDependencyReportContainer;
import org.gradle.api.reporting.dependencies.internal.HtmlDependencyReporter;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.diagnostics.AbstractDependencyReportTask;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.ProjectsWithConfigurations;
import org.gradle.internal.Describables;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.serialization.Cached;
import org.gradle.util.internal.ClosureBackedAction;

import javax.inject.Inject;
import java.util.stream.Stream;

/**
 * Generates an HTML dependency report. This report
 * combines the features of the ASCII dependency report and those of the ASCII
 * dependency insight report. For a given project, it generates a tree of the dependencies
 * of every configuration, and each dependency can be clicked to show the insight of
 * this dependency.
 * <p>
 * This task generates a report for the task's containing project by default. But it can also generate
 * a report for multiple projects, by setting the value of the
 * <code>projects</code> property. Here's how to generate an HTML
 * dependency report for all the projects of a multi-project build, for example:
 * <pre>
 * htmlDependencyReport {
 *     projects = project.allprojects
 * }
 * </pre>
 * <p>
 * The report is generated in the <code>build/reports/project/dependencies</code> directory by default.
 * This can also be changed by setting the <code>reports.html.destination</code> property:
 * <pre>
 * htmlDependencyReport {
 *     reports.html.outputLocation = file("build/reports/project/dependencies")
 * }
 * </pre>
 */
@UntrackedTask(because = "We can't describe the dependency tree of all projects as input")
public abstract class HtmlDependencyReportTask extends AbstractDependencyReportTask implements Reporting<DependencyReportContainer> {
    private final Cached<ProjectsWithConfigurations<ProjectDetails.ProjectNameAndPath, ConfigurationDetails>> projectsWithConfigurations = Cached.of(this::computeProjectsWithConfigurations);
    private final DependencyReportContainer reports;

    public HtmlDependencyReportTask() {
        reports = getObjectFactory().newInstance(DefaultDependencyReportContainer.class, Describables.quoted("Task", getIdentityPath()));
        reports.getHtml().getRequired().set(true);
    }

    @Nested
    @Override
    public DependencyReportContainer getReports() {
        return reports;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public DependencyReportContainer reports(Closure closure) {
        return reports(new ClosureBackedAction<>(closure));
    }

    @Override
    public DependencyReportContainer reports(Action<? super DependencyReportContainer> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    @Inject
    protected abstract VersionSelectorScheme getVersionSelectorScheme();

    @Inject
    protected abstract VersionComparator getVersionComparator();

    @Inject
    protected abstract VersionParser getVersionParser();

    /**
     * Required for decorating reports container callbacks for tracing user code application.
     *
     * @since 5.1
     */
    @Inject
    protected CollectionCallbackActionDecorator getCallbackActionDecorator() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void generate() {
        if (!reports.getHtml().getRequired().get()) {
            setDidWork(false);
            return;
        }

        HtmlDependencyReporter reporter = new HtmlDependencyReporter(getVersionSelectorScheme(), getVersionComparator(), getVersionParser());
        reporter.render(projectsWithConfigurations.get(), reports.getHtml().getOutputLocation().getAsFile().get());

        getLogger().lifecycle("See the report at: {}", new ConsoleRenderer().asClickableFileUrl(reports.getHtml().getEntryPoint()));
    }

    private ProjectsWithConfigurations<ProjectDetails.ProjectNameAndPath, ConfigurationDetails> computeProjectsWithConfigurations() {
        return ProjectsWithConfigurations.from(
            getProjects().get(),
            ProjectDetails::withNameAndPath,
            HtmlDependencyReportTask::getConfigurationsWhichCouldHaveDependencyInfo
        );
    }

    private static Stream<? extends ConfigurationDetails> getConfigurationsWhichCouldHaveDependencyInfo(Project project) {
        return project.getConfigurations().stream()
            .map(ConfigurationInternal.class::cast)
            .filter(c -> c.isDeclarableByExtension())
            .map(ConfigurationDetails::of);
    }
}
