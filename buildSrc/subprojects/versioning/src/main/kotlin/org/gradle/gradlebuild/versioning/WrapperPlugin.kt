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
import org.gradle.api.tasks.wrapper.Wrapper
import com.google.gson.Gson

import org.gradle.kotlin.dsl.*
import java.net.URL


class WrapperPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        wrapperUpdateTask("nightly", "nightly")
        wrapperUpdateTask("rc", "release-candidate")
        wrapperUpdateTask("current", "current")

        tasks.withType<Wrapper>() {
            val jvmOpts = "-Xmx128m -Dfile.encoding=UTF-8"
            inputs.property("jvmOpts", jvmOpts)
            // TODO Do we want to use doLast or a finalizedBy task?
            doLast {
                val optsEnvVar = "DEFAULT_JVM_OPTS"
                scriptFile.writeText(scriptFile.readText().replace("$optsEnvVar=\"\"", "$optsEnvVar=\"$jvmOpts\""))
                batchScript.writeText(batchScript.readText().replace("set $optsEnvVar=", "set $optsEnvVar=$jvmOpts"))
            }
        }
    }

    private
    fun Project.wrapperUpdateTask(name: String, label: String) {
        val wrapperTaskName = "${name}Wrapper"
        val configureWrapperTaskName = "configure${wrapperTaskName.capitalize()}"

        val wrapperTask = task<Wrapper>(wrapperTaskName) {
            dependsOn(configureWrapperTaskName)
            group = "wrapper"
        }

        // TODO Avoid late configuration
        task(configureWrapperTaskName) {
            doLast {
                val jsonText = URL("https://services.gradle.org/versions/$label").readText()
                val versionInfo = Gson().fromJson(jsonText, VersionDownloadInfo::class.java)
                println("updating wrapper to $label version: ${versionInfo.version} (downloadUrl: ${versionInfo.downloadUrl})")
                wrapperTask.distributionUrl = versionInfo.downloadUrl
            }
        }
    }
}


private
data class VersionDownloadInfo(val version: String, val downloadUrl: String)
