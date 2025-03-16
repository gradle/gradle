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

package org.gradle.api.reporting;

import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reporting.internal.BuildDashboardGenerator;
import org.gradle.api.reporting.internal.DefaultBuildDashboardReports;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates build dashboard report.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class GenerateBuildDashboard extends DefaultTask implements Reporting<BuildDashboardReports> {
    private final Set<Reporting<? extends ReportContainer<?>>> aggregated = new LinkedHashSet<Reporting<? extends ReportContainer<?>>>();

    private final BuildDashboardReports reports;

    public GenerateBuildDashboard() {
        reports = getProject().getObjects().newInstance(DefaultBuildDashboardReports.class, Describables.quoted("Task", getIdentityPath()));
        reports.getHtml().getRequired().set(true);
    }

    @Internal
    @Deprecated
    protected Instantiator getInstantiator() {
        DeprecationLogger.deprecateMethod(GenerateBuildDashboard.class, "getInstantiator()")
            .willBeRemovedInGradle9()
            .undocumented()
            .nagUser();

        return ((ProjectInternal) getProject()).getServices().get(Instantiator.class);
    }

    @Internal
    @Deprecated
    protected CollectionCallbackActionDecorator getCollectionCallbackActionDecorator() {
        DeprecationLogger.deprecateMethod(GenerateBuildDashboard.class, "getCollectionCallbackActionDecorator()")
            .willBeRemovedInGradle9()
            .undocumented()
            .nagUser();

        return ((ProjectInternal) getProject()).getServices().get(CollectionCallbackActionDecorator.class);
    }

    @Input
    @ToBeReplacedByLazyProperty(unreported = true, comment = "Skipped for report since ReportState is private")
    public Set<ReportState> getInputReports() {
        Set<ReportState> inputs = new LinkedHashSet<ReportState>();
        for (Report report : getEnabledInputReports()) {
            if (getReports().contains(report)) {
                // A report to be generated, ignore
                continue;
            }
            File outputLocation = report.getOutputLocation().get().getAsFile();
            inputs.add(new ReportState(report.getDisplayName(), outputLocation, outputLocation.exists()));
        }
        return inputs;
    }

    private Set<Report> getEnabledInputReports() {
        HashSet<Reporting<? extends ReportContainer<?>>> allAggregatedReports = Sets.newHashSet(aggregated);
        allAggregatedReports.addAll(getAggregatedTasks());

        Set<NamedDomainObjectSet<? extends Report>> enabledReportSets = CollectionUtils.collect(
            allAggregatedReports, reporting -> reporting.getReports().getEnabled()
        );
        return new LinkedHashSet<Report>(CollectionUtils.flattenCollections(Report.class, enabledReportSets));
    }

    private Set<Reporting<? extends ReportContainer<?>>> getAggregatedTasks() {
        final Set<Reporting<? extends ReportContainer<?>>> reports = new HashSet<>();
        getProject().allprojects(new Action<Project>() {
            @Override
            public void execute(Project project) {
                project.getTasks().all(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        if (!(task instanceof Reporting)) {
                            return;
                        }
                        reports.add(Cast.uncheckedNonnullCast(task));
                    }
                });
            }
        });
        return reports;
    }

    /**
     * Configures which reports are to be aggregated in the build dashboard report generated by this task.
     *
     * <pre>
     * buildDashboard {
     *   aggregate codenarcMain, checkstyleMain
     * }
     * </pre>
     *
     * @param reportings an array of {@link Reporting} instances that are to be aggregated
     */
    @SuppressWarnings("unchecked")
    // TODO Use @SafeVarargs and make method final
    public void aggregate(Reporting<? extends ReportContainer<?>>... reportings) {
        aggregated.addAll(Arrays.asList(reportings));
    }

    /**
     * The reports to be generated by this task.
     *
     * @return The reports container
     */
    @Nested
    @Override
    public BuildDashboardReports getReports() {
        return reports;
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures.
     *
     * <pre>
     * buildDashboard {
     *   reports {
     *     html {
     *       destination "build/dashboard.html"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param closure The configuration
     * @return The reports container
     */
    @Override
    public BuildDashboardReports reports(Closure closure) {
        return reports(new ClosureBackedAction<BuildDashboardReports>(closure));
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures.
     *
     * <pre>
     * buildDashboard {
     *   reports {
     *     html {
     *       destination "build/dashboard.html"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param configureAction The configuration
     * @return The reports container
     */
    @Override
    public BuildDashboardReports reports(Action<? super BuildDashboardReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    @TaskAction
    void run() {
        if (getReports().getHtml().getRequired().get()) {
            BuildDashboardGenerator generator = new BuildDashboardGenerator();
            generator.render(getEnabledInputReports(), reports.getHtml().getEntryPoint());
        } else {
            setDidWork(false);
        }
    }

    private static class ReportState implements Serializable {
        private final String name;
        private final File destination;
        private final boolean available;

        private ReportState(String name, File destination, boolean available) {
            this.name = name;
            this.destination = destination;
            this.available = available;
        }

        @Override
        public boolean equals(Object obj) {
            ReportState other = (ReportState) obj;
            return name.equals(other.name) && destination.equals(other.destination) && available == other.available;
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ destination.hashCode();
        }
    }
}
