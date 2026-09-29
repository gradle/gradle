/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.performance.results

/**
 * A row in the performance execution database that recorded no usable measurement - the scenario errored out
 * before it could measure anything (an Android Studio sync that cannot start, a test project that no longer
 * builds), rather than running to completion and regressing.
 *
 * {@link CrossVersionPerformanceTestRunner} reports in a {@code finally} block, so such a run still writes its
 * execution row, stamped with the {@code teamCityBuildId} of the build that actually ran it. That makes these rows
 * the trustworthy record of "this pipeline ran the scenario and it blew up": a build-cache hit never forks a test
 * JVM, so it can neither write nor replay one.
 *
 * Deliberately not a {@link PerformanceReportScenarioHistoryExecution}: that type's difference/confidence
 * accessors divide through the measured series and cannot be evaluated when there are none.
 */
class UnmeasuredExecution {
    final String teamCityBuildId
    final String commitId

    UnmeasuredExecution(String teamCityBuildId, String commitId) {
        this.teamCityBuildId = teamCityBuildId
        this.commitId = commitId
    }
}
