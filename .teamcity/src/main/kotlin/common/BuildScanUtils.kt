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

package common

import model.Stage
import model.StageName
import model.TestCoverage

fun buildScanTagParam(tag: String) = """-Dscan.tag.$tag"""

fun buildScanCustomValueParam(
    key: String,
    value: String,
) = """-Dscan.value.$key=$value"""

fun TestCoverage.asBuildScanCustomValue() =
    testType.name.toCamelCase().toCapitalized() +
        testJvmVersion.toCapitalized() +
        "${vendor.displayName}${os.asName()}${arch.asName()}"

// Generates a build scan custom value "PartOf=X,Y,Z"
// where X, Y, Z are all the stages including current stage
// For example, for the stage PullRequestFeedback, the custom value will be "PartOf=PullRequestFeedback,ReadyForNightly,ReadyForRelease"
private fun Stage.getBuildScanCustomValues(): List<String> =
    StageName
        .values()
        .slice(this.stageName.ordinal until StageName.READY_FOR_RELEASE.ordinal + 1)
        .map { it.uuid }

fun Stage.getBuildScanCustomValueParam(testCoverage: TestCoverage? = null): String {
    val customValues =
        if (testCoverage != null) {
            listOf(testCoverage.asBuildScanCustomValue()) + getBuildScanCustomValues()
        } else {
            getBuildScanCustomValues()
        }
    return "-DbuildScan.PartOf=${customValues.joinToString(",")}"
}
