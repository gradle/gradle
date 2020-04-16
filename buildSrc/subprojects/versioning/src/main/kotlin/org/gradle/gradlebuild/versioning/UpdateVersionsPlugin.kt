/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.gradlebuild.versioning

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.build.ReleasedVersion
import org.gradle.build.UpdateReleasedVersions
import org.gradle.build.remote.VersionType
import com.google.gson.Gson
import org.gradle.kotlin.dsl.*
import java.net.URL


class UpdateVersionsPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val releasedVersionsJsonFile = file("released-versions.json")

        tasks.withType<UpdateReleasedVersions>().configureEach {
            releasedVersionsFile = releasedVersionsJsonFile
            group = "Versioning"
        }

        tasks.register<UpdateReleasedVersions>("updateReleasedVersions") {
            val currentReleasedVersionProperty = project.findProperty("currentReleasedVersion")
            val value =
                if (currentReleasedVersionProperty != null) ReleasedVersion(currentReleasedVersionProperty.toString(), project.findProperty("currentReleasedVersionBuildTimestamp").toString())
                else null
            currentReleasedVersion.set(value)
        }

        tasks.register<UpdateReleasedVersions>("updateReleasedVersionsToLatestNightly") {
            currentReleasedVersion.set(project.provider {
                val jsonText = URL("https://services.gradle.org/versions/${VersionType.NIGHTLY.type}").readText()
                println(jsonText)
                val versionInfo = Gson().fromJson(jsonText, VersionBuildTimeInfo::class.java)
                ReleasedVersion(versionInfo.version, versionInfo.buildTime)
            })
        }

        tasks.register<UpdateBranchStatus>("updateBranchStatus")

        tasks.register<UpdateAgpVersions>("updateAgpVersions") {
            comment.set(" Generated - Update by running `./gradlew updateAgpVersions`")
            minimumSupportedMinor.set("3.4")
            propertiesFile.set(layout.projectDirectory.file("gradle/dependency-management/agp-versions.properties"))
        }
    }
}


private
class VersionBuildTimeInfo(val version: String, val buildTime: String)
