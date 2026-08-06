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

package gradlebuild.performance

import groovy.transform.CompileStatic
import groovy.transform.MapConstructor

// Modify this class with care, see class org.gradle.performance.results.PerformanceTestExecutionResult
//
// This is the output of the (cacheable) PerformanceTest task. The producing build's teamCityBuildId and the web URL
// derived from it are deliberately NOT stored here - they are build-specific and would be replayed stale onto an
// unrelated build on a build-cache hit. The report derives the TeamCity build from the
// `org.gradle.performance.dependencyBuildIds` system property. The pass/fail status is still recorded: it is the
// failure signal for cross-build scenarios, which have no version-comparison model to derive a verdict from.
@MapConstructor
@CompileStatic
class ScenarioBuildResultData {
    String scenarioName
    String scenarioClass
    String testProject
    String testFailure
    // SUCCESS/FAILURE/UNKNOWN
    String status
    String agentName
    String agentUrl
}
