/*
 * Copyright 2025 the original author or authors.
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

import configurations.FunctionalTest
import configurations.ParallelizationMethod
import java.util.SortedSet

class TestClassTime(
    val sourceSet: String,
    val buildTimeMs: Int,
)

data class TestCoverageAndBucketSplits(
    val testCoverageUuid: Int,
    val buckets: List<FunctionalTestBucket>,
)

data class FunctionalTestBucket(
    val subprojects: SortedSet<String>,
    val parallelizationMethod: ParallelizationMethod,
) {
    constructor(subprojectList: List<String>, parallelizationMethod: ParallelizationMethod) : this(
        subprojectList.toSortedSet(),
        parallelizationMethod,
    )

    constructor(jsonObject: Map<String, Any>) : this(
        (jsonObject["subprojects"] as List<*>).map { it.toString() },
        ParallelizationMethod.fromJson(jsonObject),
    )

    fun toBuildTypeBucket(gradleSubprojectProvider: GradleSubprojectProvider): SmallSubprojectBucket =
        SmallSubprojectBucket(
            subprojects.map { gradleSubprojectProvider.getSubprojectByName(it)!! },
            parallelizationMethod,
        )
}

class SubprojectTestClassTime(
    val subProject: GradleSubproject,
    testClassTimes: List<TestClassTime> = emptyList(),
) {
    val totalTime: Int = testClassTimes.sumOf { it.buildTimeMs }

    override fun toString(): String = "SubprojectTestClassTime(subProject=${subProject.name}, totalTime=$totalTime)"
}

data class SmallSubprojectBucket(
    val subprojects: List<GradleSubproject>,
    val parallelizationMethod: ParallelizationMethod,
) : BuildTypeBucket {
    constructor(
        subproject: GradleSubproject,
        parallelizationMethod: ParallelizationMethod,
    ) : this(listOf(subproject), parallelizationMethod)

    val name = truncateName(subprojects.joinToString(","))

    private fun truncateName(str: String) =
        // Can't exceed Linux file name limit 255 char on TeamCity
        if (str.length > 200) {
            str.substring(0, 200) + "..."
        } else {
            str
        }

    override fun createFunctionalTestsFor(
        model: CIBuildModel,
        stage: Stage,
        testCoverage: TestCoverage,
        bucketIndex: Int,
    ): FunctionalTest =
        FunctionalTest(
            model,
            testCoverage.getBucketUuid(model, bucketIndex),
            getName(testCoverage),
            getDescription(testCoverage),
            testCoverage,
            stage,
            parallelizationMethod,
            subprojects.map { it.name },
        )

    override fun getName(testCoverage: TestCoverage) =
        truncateName("${testCoverage.asName()} (${subprojects.joinToString(",") { it.name }})")

    override fun getDescription(testCoverage: TestCoverage) = "${testCoverage.asName()} for ${subprojects.joinToString(", ") { it.name }}"

    fun toJsonBucket(): FunctionalTestBucket = FunctionalTestBucket(subprojects.map { it.name }, parallelizationMethod)
}
