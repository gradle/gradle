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
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
// Using star import to workaround https://youtrack.jetbrains.com/issue/KTIJ-24390
import org.gradle.kotlin.dsl.*
import org.gradle.work.DisableCachingByDefault
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone


@DisableCachingByDefault(because = "Not worth caching")
abstract class BuildReceipt : DefaultTask() {
    companion object {
        private
        val timestampFormat = newSimpleDateFormatUTC("yyyyMMddHHmmssZ")

        private
        val isoTimestampFormat = newSimpleDateFormatUTC("yyyy-MM-dd HH:mm:ss z")

        private
        fun newSimpleDateFormatUTC(pattern: String) = SimpleDateFormat(pattern).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

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

    @get:Input
    @get:Optional
    abstract val buildTimestamp: Property<Date>

    @get:OutputDirectory
    abstract val receiptFolder: DirectoryProperty

    fun buildTimestampFrom(provider: Provider<String>) {
        buildTimestamp = provider.map { buildTimestampString -> timestampFormat.parse(buildTimestampString) }
    }

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
                put("buildTimestamp", getBuildTimestampAsString())
                put("buildTimestampIso", getBuildTimestampAsIsoString())
            },
            file
        )
    }

    private
    fun getBuildTimestampAsString() =
        buildTimestamp.get().let { timestampFormat.format(it) }

    private
    fun getBuildTimestampAsIsoString() =
        buildTimestamp.get().let { isoTimestampFormat.format(it) }

    private
    fun Logger.logBuildVersion() {
        lifecycle(
            "Version: ${version.get()} " +
                "(base version: ${baseVersion.get()}," +
                " timestamp: ${buildTimestamp.get()}," +
                " snapshot: ${snapshot.get()})"
        )
        if (BuildEnvironment.isCiServer) {
            lifecycle(
                "##teamcity[buildStatus text='{build.status.text}, Promoted version ${version.get()}']"
            )
        }
    }
}
