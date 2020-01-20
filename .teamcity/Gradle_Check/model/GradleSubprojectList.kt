/*
 * Copyright 2019 the original author or authors.
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

package Gradle_Check.model

import model.GradleSubproject
import model.Stage
import model.TestCoverage

data class GradleSubprojectList(val subprojects: List<GradleSubproject>) {
    private val nameToSubproject = subprojects.map { it.name to it }.toMap()
    fun getSubprojectsFor(testConfig: TestCoverage, stage: Stage) =
        subprojects.filterNot { it.containsSlowTests && stage.omitsSlowProjects }
            .filter { it.hasTestsOf(testConfig.testType) }
            .filterNot { testConfig.os.ignoredSubprojects.contains(it.name) }

    fun getSubprojectByName(name: String) = nameToSubproject[name]
    fun getSlowSubprojects() = subprojects.filter { it.containsSlowTests }

    /**
     * Get the subprojects starting between startChar (inclusive) and endChar (inclusive),
     * for example, toolingApi is in ('R','T') but not in ('A','F') because toolingApi starts with 'T'
     */
    fun getSubprojectsBetween(startChar: Char, endChar: Char): List<String> = subprojects.map { it.name }.filter { it[0].toUpperCase() in (startChar..endChar) }
}
