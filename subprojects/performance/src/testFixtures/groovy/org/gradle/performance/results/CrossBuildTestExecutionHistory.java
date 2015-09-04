/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.performance.results;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.gradle.performance.fixture.BuildDisplayInfo;
import org.gradle.performance.fixture.CrossBuildPerformanceResults;
import org.gradle.performance.fixture.MeasuredOperationList;

import javax.annotation.Nullable;
import java.util.List;

public class CrossBuildTestExecutionHistory implements TestExecutionHistory {
    private final String name;

    private final List<BuildDisplayInfo> builds;

    private final List<CrossBuildPerformanceResults> newestFirst;

    public CrossBuildTestExecutionHistory(String name, List<BuildDisplayInfo> builds, List<CrossBuildPerformanceResults> newestFirst) {
        this.name = name;
        this.builds = builds;
        this.newestFirst = newestFirst;
    }

    public List<BuildDisplayInfo> getBuilds() {
        return builds;
    }

    public List<CrossBuildPerformanceResults> getResults() {
        return newestFirst;
    }

    @Override
    public String getId() {
        return name.replaceAll("\\s+", "-");
    }

    public String getName() {
        return name;
    }

    @Override
    public List<PerformanceResults> getPerformanceResults() {
        return Lists.transform(newestFirst, new Function<CrossBuildPerformanceResults, PerformanceResults>() {
            public PerformanceResults apply(@Nullable final CrossBuildPerformanceResults results) {
                return new KnownBuildSpecificationsPerformanceResults(results);
            }
        });
    }

    @Override
    public int getExperimentCount() {
        return builds.size();
    }

    @Override
    public List<String> getExperimentLabels() {
        return Lists.transform(builds, new Function<BuildDisplayInfo, String>() {
            public String apply(@Nullable BuildDisplayInfo specification) {
                return specification.getDisplayName();
            }
        });
    }

    private class KnownBuildSpecificationsPerformanceResults implements PerformanceResults {
        private final CrossBuildPerformanceResults results;

        public KnownBuildSpecificationsPerformanceResults(CrossBuildPerformanceResults results) {
            this.results = results;
        }

        @Override
        public String getVersionUnderTest() {
            return results.getVersionUnderTest();
        }

        @Override
        public String getVcsBranch() {
            return results.getVcsBranch();
        }

        @Override
        public long getTestTime() {
            return results.getTestTime();
        }

        @Override
        public String getVcsCommit() {
            return results.getVcsCommit();
        }

        @Override
        public List<MeasuredOperationList> getExperiments() {
            return Lists.transform(builds, new Function<BuildDisplayInfo, MeasuredOperationList>() {
                @Override
                public MeasuredOperationList apply(@Nullable BuildDisplayInfo specification) {
                    return results.buildResult(specification);
                }
            });
        }

        @Override
        public String getOperatingSystem() {
            return results.getOperatingSystem();
        }

        @Override
        public String getJvm() {
            return results.getJvm();
        }
    }
}
