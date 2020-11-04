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
import org.gradle.api.internal.dependencies.DependencyHealthCollector;
import org.gradle.api.internal.dependencies.DependencyHealthStatistics;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.concurrent.Stoppable;

import java.util.ArrayList;
import java.util.List;

public class DefaultDependencyHealthCollector implements DependencyHealthCollector, DependencyResolutionListener, Stoppable {
    private final Gradle gradle;
    private final DependencyHealthAnalyzer analyzer;
    private final DefaultDependencyHealthStatistics statistics;
    private final List<String> suppressedIds;

    public DefaultDependencyHealthCollector(Gradle gradle, DependencyHealthAnalyzer analyzer) {
        this.gradle = gradle;
        this.analyzer = analyzer;
        this.statistics = new DefaultDependencyHealthStatistics();
        this.suppressedIds = new ArrayList<>();
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
        for (DependencyResult dependency : dependencies.getResolutionResult().getRoot().getDependencies()) {
            if (dependency instanceof ResolvedDependencyResult) {
                for (Capability capability : ((ResolvedDependencyResult) dependency).getResolvedVariant().getCapabilities()) {
                    DependencyHealthAnalyzer.HealthReport report = analyzer.analyze(capability.getGroup(), capability.getName(), capability.getVersion());
                    for (DependencyHealthAnalyzer.Cve cve : report.getCves()) {
                        if (cve.getScore() < 3.9) {
                            statistics.low++;
                        } else if (cve.getScore() < 6.9) {
                            statistics.medium++;
                        } else if (cve.getScore() < 8.9) {
                            statistics.high++;
                        } else {
                            statistics.critical++;
                        }
                        if (suppressedIds.contains(cve.getId())) {
                            statistics.suppressed++;
                        }
                        statistics.total++;
                    }
                }
            }
        }
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
}
