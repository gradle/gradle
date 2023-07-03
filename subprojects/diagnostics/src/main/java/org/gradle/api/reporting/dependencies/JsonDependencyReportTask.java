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

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reporting.dependencies.internal.JsonDependencyReporter;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.diagnostics.ConventionReportTask;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.ProjectsWithConfigurations;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.serialization.Cached;
import org.gradle.internal.serialization.Transient;

import javax.inject.Inject;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Collections.singleton;
import static org.gradle.internal.Cast.uncheckedCast;

/**
 * Generates an JSON dependency report. This report
 * combines the features of the ASCII dependency report and those of the ASCII
 * dependency insight report. For a given project, it generates a tree of the dependencies
 * of every configuration.
 * <p>
 * This task generates a report for the task's containing project by default. But it can also generate
 * a report for multiple projects, by setting the value of the
 * <code>projects</code> property. Here's how to generate an JSON
 * dependency report for all the projects of a multi-project build, for example:
 * <pre>
 * jsonDependencyReport {
 *     projects = project.allprojects
 * }
 * </pre>
 * <p>
 * The report is generated to <code>build/reports/project/dependencies.json</code> by default.
 *
 * @since 8.3
 */
@UntrackedTask(because = "We can't describe the dependency tree of all projects as input")
public abstract class JsonDependencyReportTask extends ConventionReportTask {
    private final Transient.Var<Set<Project>> projects = Transient.varOf(uncheckedCast(singleton(getProject())));
    private final Cached<ProjectsWithConfigurations<ProjectDetails.ProjectNameAndPath, ConfigurationDetails>> projectsWithConfigurations = Cached.of(this::computeProjectsWithConfigurations);
    private final DirectoryProperty reportDir;

    public JsonDependencyReportTask() {
        reportDir = getObjectFactory().directoryProperty();
    }

    @Override
    protected ReportRenderer getRenderer() {
        throw new UnsupportedOperationException();
    }

    @Internal
    public DirectoryProperty getProjectReportDirectory() {
        return reportDir;
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
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
    protected  VersionParser getVersionParser() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void generate() {
        JsonDependencyReporter reporter = new JsonDependencyReporter(getVersionSelectorScheme(), getVersionComparator(), getVersionParser());
        reporter.render(projectsWithConfigurations.get(), getOutputFile());
        getLogger().lifecycle("See the report at: {}", new ConsoleRenderer().asClickableFileUrl(getOutputFile()));
    }

    /**
     * Returns the set of projects to generate a report for. By default, the report is generated for the task's
     * containing project.
     *
     * @return The set of files.
     */
    @Internal
    public Set<Project> getProjects() {
        return projects.get();
    }

    /**
     * Specifies the set of projects to generate this report for.
     *
     * @param projects The set of projects. Must not be null.
     */
    public void setProjects(Set<Project> projects) {
        this.projects.set(projects);
    }

    private ProjectsWithConfigurations<ProjectDetails.ProjectNameAndPath, ConfigurationDetails> computeProjectsWithConfigurations() {
        return ProjectsWithConfigurations.from(
            getProjects(),
            ProjectDetails::withNameAndPath,
            JsonDependencyReportTask::getConfigurationsWhichCouldHaveDependencyInfo
        );
    }

    private static Stream<? extends ConfigurationDetails> getConfigurationsWhichCouldHaveDependencyInfo(Project project) {
        return project.getConfigurations().stream()
            .map(ConfigurationInternal.class::cast)
            .filter(c -> c.isDeclarableByExtension())
            .map(ConfigurationDetails::of);
    }

    private boolean shouldCreateReportFile() {
        return getOutputFile() != null;
    }
}
