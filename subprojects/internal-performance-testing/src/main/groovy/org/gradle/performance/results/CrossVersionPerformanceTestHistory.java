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

package org.gradle.performance.results;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CrossVersionPerformanceTestHistory implements PerformanceTestHistory {
    private final String name;
    private final List<String> versions;
    private final List<String> branches;
    private final List<CrossVersionPerformanceResults> newestFirst;
    private List<CrossVersionPerformanceResults> oldestFirst;
    private List<String> knownVersions;

    public CrossVersionPerformanceTestHistory(String name, List<String> versions, List<String> branches, List<CrossVersionPerformanceResults> newestFirst) {
        this.name = name;
        this.versions = versions;
        this.branches = branches;
        this.newestFirst = newestFirst;
    }

    @Override
    public String getId() {
        return name.replaceAll("\\s+", "-");
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    public List<String> getBaselineVersions() {
        return versions;
    }

    public List<String> getBranches() {
        return branches;
    }

    public List<String> getKnownVersions() {
        if (knownVersions == null) {
            ArrayList<String> result = new ArrayList<String>();
            result.addAll(versions);
            result.addAll(branches);
            knownVersions = result;
        }
        return knownVersions;
    }

    /**
     * Returns results from most recent to least recent.
     */
    public List<CrossVersionPerformanceResults> getResults() {
        return newestFirst;
    }

    /**
     * Returns results from least recent to most recent.
     */
    public List<CrossVersionPerformanceResults> getResultsOldestFirst() {
        if (oldestFirst == null) {
            oldestFirst = new ArrayList<CrossVersionPerformanceResults>(newestFirst);
            Collections.reverse(oldestFirst);
        }
        return oldestFirst;
    }

    @Override
    public List<PerformanceTestExecution> getExecutions() {
        return Lists.transform(getResults(), new Function<CrossVersionPerformanceResults, PerformanceTestExecution>() {
            public PerformanceTestExecution apply(final CrossVersionPerformanceResults result) {
                return new KnownVersionsPerformanceTestExecution(result);
            }
        });
    }

    @Override
    public List<? extends ScenarioDefinition> getScenarios() {
        if (newestFirst.isEmpty()) {
            return Collections.emptyList();
        }
        final CrossVersionPerformanceResults mostRecent = newestFirst.get(0);
        return Lists.transform(getKnownVersions(), new Function<String, ScenarioDefinition>() {
            @Override
            public ScenarioDefinition apply(final String input) {
                return new ScenarioDefinition() {
                    @Override
                    public String getDisplayName() {
                        return input;
                    }

                    @Override
                    public String getTestProject() {
                        return mostRecent.getTestProject();
                    }

                    @Override
                    public List<String> getTasks() {
                        return mostRecent.getTasks();
                    }

                    @Override
                    public List<String> getCleanTasks() {
                        return mostRecent.getCleanTasks();
                    }

                    @Override
                    public List<String> getArgs() {
                        return mostRecent.getArgs();
                    }

                    @Nullable
                    @Override
                    public List<String> getGradleOpts() {
                        return mostRecent.getGradleOpts();
                    }

                    @Nullable
                    @Override
                    public Boolean getDaemon() {
                        return mostRecent.getDaemon();
                    }
                };
            }
        });
    }

    @Override
    public int getScenarioCount() {
        return getKnownVersions().size();
    }

    @Override
    public List<String> getScenarioLabels() {
        return getKnownVersions();
    }

    private class KnownVersionsPerformanceTestExecution implements PerformanceTestExecution {
        private final CrossVersionPerformanceResults result;

        public KnownVersionsPerformanceTestExecution(CrossVersionPerformanceResults result) {
            this.result = result;
        }

        @Override
        public String getExecutionId() {
            return String.valueOf(Math.abs(getVcsCommits() != null ? getVcsCommits().hashCode() : hashCode()));
        }

        public String getVersionUnderTest() {
            return result.getVersionUnderTest();
        }

        public String getVcsBranch() {
            return result.getVcsBranch();
        }

        public long getStartTime() {
            return result.getStartTime();
        }

        @Override
        public long getEndTime() {
            return result.getEndTime();
        }

        @Override
        public List<String> getVcsCommits() {
            return result.getVcsCommits();
        }

        public List<MeasuredOperationList> getScenarios() {
            return Lists.transform(getKnownVersions(), new Function<String, MeasuredOperationList>() {
                public MeasuredOperationList apply(String version) {
                    return result.version(version).getResults();
                }
            });
        }

        @Override
        public String getOperatingSystem() {
            return result.getOperatingSystem();
        }

        @Override
        public String getHost() {
            return result.getHost();
        }

        @Override
        public String getJvm() {
            return result.getJvm();
        }

        @Nullable
        @Override
        public List<String> getArgs() {
            return result.getArgs();
        }

        @Nullable
        @Override
        public String getTestProject() {
            return result.getTestProject();
        }

        @Nullable
        @Override
        public List<String> getTasks() {
            return result.getTasks();
        }

        @Nullable
        @Override
        public List<String> getCleanTasks() {
            return result.getCleanTasks();
        }

        @Nullable
        @Override
        public List<String> getGradleOpts() {
            return result.getGradleOpts();
        }

        @Nullable
        @Override
        public Boolean getDaemon() {
            return result.getDaemon();
        }
    }
}
