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

package gradlebuild.buildutils.tasks

import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.util.internal.VersionNumber
import java.util.Properties

/**
 * Fetch the latest versions for smoke tested plugins and write a properties file.
 * Never up-to-date, non-cacheable.
 */
@UntrackedTask(because = "Not worth tracking")
abstract class UpdateSmokeTestedPluginsVersions : AbstractVersionsUpdateTask() {

    @TaskAction
    fun update() {
        val pluginIds = getPluginIds()
        val fetchedVersions = fetchLatestVersions(pluginIds)
        updateProperties(fetchedVersions)
    }

    private
    fun getPluginIds(): List<String> {
        val props = Properties()
        propertiesFile.get().asFile.reader().use { input ->
            props.load(input)
        }
        return props.keys.map { it as String }.sorted()
    }

    private
    fun fetchLatestVersions(pluginIds: List<String>): List<TestedVersion> =
        pluginIds
            .map { pluginId ->
                val metadataUrl = "https://plugins.gradle.org/m2/${pluginId.replace('.', '/')}/$pluginId.gradle.plugin/maven-metadata.xml"
                val latest = fetchVersionsFromMavenMetadata(metadataUrl)
                    .maxByOrNull { VersionNumber.parse(it) }
                    ?: error("No version found for plugin $pluginId")

                TestedVersion(pluginId, latest)
            }

    private
    fun updateProperties(fetchedVersions: List<TestedVersion>) = updateProperties {
        fetchedVersions.forEach { (pluginId, version) ->
            setProperty(pluginId, version)
        }
    }

    private data class TestedVersion(
        val pluginId: String,
        val version: String
    )
}
