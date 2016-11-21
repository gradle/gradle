/*
 * Copyright 2016 the original author or authors.
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

import groovy.transform.CompileStatic

@CompileStatic
class GradleVsMavenBuildPerformanceResults extends CrossBuildPerformanceResults {

    void assertComparesWithMaven(double maxTimeDifference, double maxMemoryDifference) {
        builds.groupBy { it.displayName - 'Gradle ' - 'Maven ' }.each { scenario, infos ->
            def gradle = buildResults[infos.find { it.displayName.startsWith 'Gradle ' }]
            def maven = buildResults[infos.find { it.displayName.startsWith 'Maven ' }]
            def baselineVersion = new BaselineVersion("Maven")
            baselineVersion.results.addAll(maven)
            def stats = baselineVersion.getSpeedStatsAgainst("Gradle", gradle)

            println stats

            def mavenIsFaster = baselineVersion.fasterThan(gradle)
            if (mavenIsFaster) {
                throw new AssertionError(stats as Object)
            }
        }
    }

}
