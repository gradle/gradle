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

package Gradle_Check.model

import common.Os
import model.CIBuildModel
import model.PerformanceTestType
import java.util.Locale

data class PerformanceTestCoverage(
    val uuid: Int,
    val type: PerformanceTestType,
    val os: Os,
    val numberOfBuckets: Int = 40,
    val oldUuid: String? = null,
    val withoutDependencies: Boolean = false
) {
    fun asConfigurationId(model: CIBuildModel, bucket: String = "") =
        "${model.projectPrefix}${oldUuid ?: "PerformanceTest$uuid"}$bucket"
    fun asName(): String =
        "${type.displayName} - ${os.asName()}"

    fun channel(branch: String = "%teamcity.build.branch%") =
        "${type.channel}${if (os == Os.LINUX) "" else "-${os.name.toLowerCase(Locale.US)}"}-$branch"
}
