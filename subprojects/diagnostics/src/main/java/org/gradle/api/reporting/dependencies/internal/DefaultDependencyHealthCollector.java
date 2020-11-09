/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.reporting.dependencies.internal;

import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ResolvableDependenciesInternal;
import org.gradle.api.internal.dependencies.DependencyHealthCollector;
import org.gradle.api.internal.dependencies.DependencyHealthStatistics;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.DependencyHealth;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.vulnerability.DependencyHealthAnalyzer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DefaultDependencyHealthCollector implements DependencyHealthCollector, DependencyResolutionListener, Stoppable {
    private final Gradle gradle;
    private final DependencyHealthAnalyzer analyzer;
    private final DefaultDependencyHealthStatistics statistics;
    private final Map<ProjectInternal, ProjectDependencyHealthCollector> projectToCollector = new HashMap<>();

    public DefaultDependencyHealthCollector(Gradle gradle, DependencyHealthAnalyzer analyzer) {
        this.gradle = gradle;
        this.analyzer = analyzer;
        this.statistics = new DefaultDependencyHealthStatistics();
        gradle.addListener(this);
    }

    @Override
    public DependencyHealthStatistics getStatistics() {
        return statistics;
    }

    @Override
    public void beforeResolve(ResolvableDependencies dependencies) {
        // do nothing
    }

    @Override
    public void afterResolve(ResolvableDependencies dependencies) {
        ResolvableDependenciesInternal dependenciesInternal = (ResolvableDependenciesInternal) dependencies;
        if (dependenciesInternal.getProjectOwner() == null) {
            // TODO: This means that we won't look at plugin dependencies. We should figure out how to fix this.
            return;
        }
        ProjectDependencyHealthCollector collector =
            projectToCollector.computeIfAbsent(dependenciesInternal.getProjectOwner(), p -> new ProjectDependencyHealthCollector(p, analyzer, statistics));
        collector.handleResolve(dependencies);
    }

    @Override
    public void stop() {
        gradle.removeListener(this);
    }

    private static class DefaultDependencyHealthStatistics implements DependencyHealthStatistics {
        private long total;
        private long suppressed;
        private long low;
        private long medium;
        private long high;
        private long critical;

        @Override
        public long getTotal() {
            return total;
        }

        @Override
        public long getSuppressed() {
            return suppressed;
        }

        @Override
        public long getLow() {
            return low;
        }

        @Override
        public long getMedium() {
            return medium;
        }

        @Override
        public long getHigh() {
            return high;
        }

        @Override
        public long getCritical() {
            return critical;
        }

        @Override
        public String toString() {
            return "health{" +
                "total=" + total +
                ", suppressed=" + suppressed +
                ", low=" + low +
                ", medium=" + medium +
                ", high=" + high +
                ", critical=" + critical +
                '}';
        }
    }

    static class ProjectDependencyHealthCollector {
        private final ProjectInternal project;
        private final DependencyHealthAnalyzer analyzer;
        private final DefaultDependencyHealthStatistics statistics;

        ProjectDependencyHealthCollector(
            ProjectInternal project,
            DependencyHealthAnalyzer analyzer,
            DefaultDependencyHealthStatistics statistics
        ) {
            this.project = project;
            this.analyzer = analyzer;
            this.statistics = statistics;
        }

        void handleResolve(ResolvableDependencies dependencies) {
            DependencyHealth dependencyHealth = project.getExtensions().getByType(DependencyHealth.class);
            Collection<String> suppressedGroups = dependencyHealth.getSuppressed().getGroups().get();
            Collection<String> suppressedCves = dependencyHealth.getSuppressed().getCves().get();

            for (DependencyResult dependency : dependencies.getResolutionResult().getRoot().getDependencies()) {
                if (dependency instanceof ResolvedDependencyResult) {
                    for (Capability capability : ((ResolvedDependencyResult) dependency).getResolvedVariant().getCapabilities()) {
                        DependencyHealthAnalyzer.HealthReport report = analyzer.analyze(capability.getGroup(), capability.getName(), capability.getVersion());

                        boolean suppressedGroup = suppressedGroups.contains(capability.getGroup());
                        for (DependencyHealthAnalyzer.Cve cve : report.getCves()) {
                            if (suppressedGroup || suppressedCves.contains(cve.getId())) {
                                statistics.suppressed++;
                            } else {
                                switch (cve.getSeverity()) {
                                    case LOW:
                                        statistics.low++;
                                        break;
                                    case MEDIUM:
                                        statistics.medium++;
                                        break;
                                    case HIGH:
                                        statistics.high++;
                                        break;
                                    case CRITICAL:
                                        statistics.critical++;
                                        break;
                                }
                            }
                            statistics.total++;
                        }
                    }
                }
            }
        }
    }
}
