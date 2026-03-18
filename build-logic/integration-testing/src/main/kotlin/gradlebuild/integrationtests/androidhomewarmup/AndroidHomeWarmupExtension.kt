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

package gradlebuild.integrationtests.androidhomewarmup

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import java.io.Serializable

/**
 * Configuration for the Android home warmup tasks.
 */
abstract class AndroidHomeWarmupExtension {
    // using project.rootProject.layout will trigger IP error "cannot access 'Project.layout' functionality on another project ':'"
    abstract val rootProjectDir: DirectoryProperty
    abstract val warmupProjectsDirectory: DirectoryProperty
    abstract val sdkVersions: ListProperty<SdkVersion>
}

data class SdkVersion(
    val compileSdk: Int,
    val buildTools: String,
    val agpVersion: String = "8.11.2",
    val minSdk: Int = 23,
    val targetSdk: Int? = null
):Serializable {
    val effectiveTargetSdk: Int
        get() = targetSdk ?: compileSdk
}
