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

import javax.annotation.Nullable;
import java.util.List;

public class CrossBuildPerformanceTestHistory implements PerformanceTestHistory {
    private final String name;
    private final List<BuildDisplayInfo> builds;

    private final List<CrossBuildPerformanceResults> newestFirst;

    public CrossBuildPerformanceTestHistory(String name, List<BuildDisplayInfo> builds, List<CrossBuildPerformanceResults> newestFirst) {
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
    public String getDisplayName() {
        return name;
    }

    @Override
    public List<PerformanceTestExecution> getExecutions() {
        return Lists.transform(newestFirst, new Function<CrossBuildPerformanceResults, PerformanceTestExecution>() {
            @Override
            public PerformanceTestExecution apply(@Nullable final CrossBuildPerformanceResults results) {
                return new KnownBuildSpecificationsPerformanceTestExecution(results);
            }
        });
    }

    @Override
    public int getScenarioCount() {
        return builds.size();
    }

    @Override
    public List<String> getScenarioLabels() {
        return Lists.transform(builds, new Function<BuildDisplayInfo, String>() {
            @Override
            public String apply(@Nullable BuildDisplayInfo specification) {
                return specification.getDisplayName();
            }
        });
    }

    @Override
    public List<? extends ScenarioDefinition> getScenarios() {
        return Lists.transform(builds, new Function<BuildDisplayInfo, ScenarioDefinition>() {
            @Override
            public ScenarioDefinition apply(final BuildDisplayInfo input) {
                return new ScenarioDefinition() {
                    @Override
                    public String getDisplayName() {
                        return input.getDisplayName();
                    }

                    @Override
                    public String getTestProject() {
                        return input.getProjectName();
                    }

                    @Override
                    public List<String> getTasks() {
                        return input.getTasksToRun();
                    }

                    @Override
                    public List<String> getCleanTasks() {
                        return input.getCleanTasks();
                    }

                    @Override
                    public List<String> getArgs() {
                        return input.getArgs();
                    }

                    @Nullable
                    @Override
                    public List<String> getGradleOpts() {
                        return input.getGradleOpts();
                    }

                    @Nullable
                    @Override
                    public Boolean getDaemon() {
                        return input.getDaemon();
                    }
                };
            }
        });
    }

    private class KnownBuildSpecificationsPerformanceTestExecution implements PerformanceTestExecution {
        private final CrossBuildPerformanceResults results;

        public KnownBuildSpecificationsPerformanceTestExecution(CrossBuildPerformanceResults results) {
            this.results = results;
        }

        @Override
        public String getExecutionId() {
            return String.valueOf(Math.abs(getVcsCommits() != null ? getVcsCommits().hashCode() : hashCode()));
        }

        @Override
        public String getTeamCityBuildId() {
            return results.getTeamCityBuildId();
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
        public long getStartTime() {
            return results.getStartTime();
        }

        @Override
        public long getEndTime() {
            return results.getEndTime();
        }

        @Override
        public List<String> getVcsCommits() {
            return results.getVcsCommits();
        }

        @Override
        public List<MeasuredOperationList> getScenarios() {
            return Lists.transform(builds, new Function<BuildDisplayInfo, MeasuredOperationList>() {
                @Override
                public MeasuredOperationList apply(@Nullable BuildDisplayInfo specification) {
                    return results.buildResult(specification.getDisplayName());
                }
            });
        }

        @Override
        public String getOperatingSystem() {
            return results.getOperatingSystem();
        }

        @Override
        public String getHost() {
            return results.getHost();
        }

        @Override
        public String getJvm() {
            return results.getJvm();
        }

        @Nullable
        @Override
        public String getTestProject() {
            return null;
        }

        @Nullable
        @Override
        public List<String> getTasks() {
            return null;
        }

        @Nullable
        @Override
        public List<String> getCleanTasks() {
            return null;
        }

        @Nullable
        @Override
        public List<String> getArgs() {
            return null;
        }

        @Nullable
        @Override
        public List<String> getGradleOpts() {
            return null;
        }

        @Nullable
        @Override
        public Boolean getDaemon() {
            return null;
        }
    }
}
