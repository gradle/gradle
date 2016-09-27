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

import org.gradle.api.Nullable;

import java.util.List;

/**
 * The result of a single execution of a performance test.
 */
public interface PerformanceTestExecution {
    /**
     * Returns a unique identifier for this execution, suitable to be used as HTML id
     */
    String getExecutionId();

    String getVersionUnderTest();
    String getVcsBranch();
    long getStartTime();
    long getEndTime();

    List<String> getVcsCommits();

    /**
     * Returns the results of the scenarios executed as part of this performance test.
     */
    List<MeasuredOperationList> getScenarios();

    String getOperatingSystem();

    String getJvm();

    /**
     * The test project name. Null if not known or not constant for all experiments
     */
    @Nullable
    String getTestProject();

    /**
     * The tasks executed. Null if not known or not constant for all experiments
     */
    @Nullable
    List<String> getTasks();

    /**
     * The Gradle arguments. Null if not known or not constant for all experiments
     */
    @Nullable
    List<String> getArgs();

    /**
     * The Gradle JVM args. Null if not known or not constant for all experiments
     */
    @Nullable
    List<String> getGradleOpts();

    /**
     * Was the daemon used. Null if not known or not constant for all experiments
     */
    @Nullable
    Boolean getDaemon();

}
