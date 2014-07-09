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
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.reporting.dependencies.internal.DefaultDependencyReportContainer;
import org.gradle.api.reporting.dependencies.internal.HtmlDependencyReporter;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.util.Set;

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
 * This can also be changed by setting the <code>outputDirectory</code>
 * property.
 */
@Incubating
public class HtmlDependencyReportTask extends ConventionTask implements Reporting<DependencyReportContainer> {
    private Set<Project> projects;
    private final DefaultDependencyReportContainer reports;

    public HtmlDependencyReportTask() {
        reports = getInstantiator().newInstance(DefaultDependencyReportContainer.class, this);
        reports.getHtml().setEnabled(true);
        getOutputs().upToDateWhen(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return false;
            }
        });
    }

    public DependencyReportContainer getReports() {
        return reports;
    }

    public DependencyReportContainer reports(Closure closure) {
        reports.configure(closure);
        return reports;
    }

    @Inject
    protected Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected VersionMatcher getVersionMatcher() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void generate() {
        if (!reports.getHtml().isEnabled()) {
            setDidWork(false);
            return;
        }

        HtmlDependencyReporter reporter = new HtmlDependencyReporter(getVersionMatcher());
        reporter.render(getProjects(), reports.getHtml().getDestination());
    }

    /**
     * Returns the set of projects to generate a report for. By default, the report is generated for the task's
     * containing project.
     *
     * @return The set of files.
     */
    public Set<Project> getProjects() {
        return projects;
    }

    /**
     * Specifies the set of projects to generate this report for.
     *
     * @param projects The set of projects. Must not be null.
     */
    public void setProjects(Set<Project> projects) {
        this.projects = projects;
    }
}
