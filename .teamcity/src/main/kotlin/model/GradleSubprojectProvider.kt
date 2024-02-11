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

package model

import com.alibaba.fastjson.JSON
import java.io.File

val ignoredSubprojects = listOf(
    "soak", // soak test
    "distributions-integ-tests", // build distribution testing
    "architecture-test" // sanity check
)

interface GradleSubprojectProvider {
    val subprojects: List<GradleSubproject>
    fun getSubprojectsForFunctionalTest(testConfig: TestCoverage): List<GradleSubproject>
    fun getSubprojectByName(name: String): GradleSubproject?
}

data class JsonBasedGradleSubprojectProvider(private val jsonFile: File) : GradleSubprojectProvider {
    @Suppress("UNCHECKED_CAST")
    override val subprojects = JSON.parseArray(jsonFile.readText()).map { toSubproject(it as Map<String, Any>) }

    private val nameToSubproject = subprojects.map { it.name to it }.toMap()
    override fun getSubprojectsForFunctionalTest(testConfig: TestCoverage) =
        subprojects.filter { it.hasTestsOf(testConfig.testType) }

    override fun getSubprojectByName(name: String) = nameToSubproject[name]

    private
    fun toSubproject(subproject: Map<String, Any>): GradleSubproject {
        val name = subproject["name"] as String
        val path = subproject["path"] as String
        val unitTests = !ignoredSubprojects.contains(name) && subproject["unitTests"] as Boolean
        val functionalTests = !ignoredSubprojects.contains(name) && subproject["functionalTests"] as Boolean
        val crossVersionTests = !ignoredSubprojects.contains(name) && subproject["crossVersionTests"] as Boolean
        return GradleSubproject(name, path, unitTests, functionalTests, crossVersionTests)
    }
}
