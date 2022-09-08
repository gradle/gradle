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

package gradlebuild.identity.tasks

import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.util.ReproduciblePropertiesWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.util.Properties


@DisableCachingByDefault(because = "Not worth caching")
abstract class BuildReceipt : DefaultTask() {
    companion object {
        fun readBuildReceiptFromString(buildReceipt: String) =
            Properties().apply { load(buildReceipt.reader()) }

        const val buildReceiptFileName = "build-receipt.properties"
        const val buildReceiptLocation = "org/gradle/$buildReceiptFileName"
    }

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val baseVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val commitId: Property<String>

    @get:Input
    abstract val snapshot: Property<Boolean>

    @get:Input
    abstract val promotionBuild: Property<Boolean>

    @get:OutputDirectory
    abstract val receiptFolder: DirectoryProperty

    @TaskAction
    fun generate() {
        if (promotionBuild.get()) {
            logger.logBuildVersion()
        }
        val file = receiptFolder.file(buildReceiptLocation).get().asFile.also {
            it.parentFile.mkdirs()
        }
        ReproduciblePropertiesWriter.store(
            Properties().apply {
                put("commitId", commitId.getOrElse("HEAD"))
                put("versionNumber", version.get())
                put("baseVersion", baseVersion.get())
                put("isSnapshot", snapshot.get().toString())
            },
            file
        )
    }

    private
    fun Logger.logBuildVersion() {
        lifecycle(
            "Version: ${version.get()} " +
                "(base version: ${baseVersion.get()}," +
                " snapshot: ${snapshot.get()})"
        )
        if (BuildEnvironment.isCiServer) {
            lifecycle(
                "##teamcity[buildStatus text='{build.status.text}, Promoted version ${version.get()}']"
            )
        }
    }
}
