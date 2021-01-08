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

import java.io.Closeable;
import java.util.List;
import java.util.Map;

public interface ResultsStore extends Closeable {
    /**
     * Returns the performance experiments known to this store, in display order.
     */
    List<PerformanceExperiment> getPerformanceExperiments();

    /**
     * Returns the full history of the given performance experiment.
     */
    PerformanceTestHistory getTestResults(PerformanceExperiment experiment, String channel);

    /**
     * Returns the n most recent instances of the given test which are younger than the max age.
     */
    PerformanceTestHistory getTestResults(PerformanceExperiment experiment, int mostRecentN, int maxDaysOld, String channel);

    /**
     * Returns the estimated duration for each experiment in milliseconds.
     */
    Map<PerformanceExperimentOnOs, Long> getEstimatedExperimentDurationsInMillis();
}
