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

package gradlebuild.packaging.transforms

import com.google.gson.Gson
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory


/**
 * Artifact transform that extracts license metadata from a Maven POM file.
 *
 * Input:  a Maven POM file (artifact type "pom")
 * Output: a JSON file "license-metadata.json" containing:
 *         { "groupId": "...", "artifactId": "...", "version": "...", "licenseName": "...", "licenseUrl": "..." }
 *         where licenseName and licenseUrl may be null if the POM has no <licenses> section.
 *         The output is an empty object {} if the POM cannot be parsed (e.g. missing coordinates).
 *
 * For components whose POM has missing or incorrect license information, add a hardcoded entry
 * to [hardcodedLicenses] below.
 */
@CacheableTransform
abstract class ExtractPomLicenseData : TransformAction<TransformParameters.None> {

    /**
     * Hardcoded license data for components with missing or incorrect POM license information.
     * Key format: "groupId:artifactId"
     * Value: Pair(licenseName, licenseUrl?) — licenseUrl may be null if not applicable.
     *
     * Add entries here when the build fails with "no license information found" for a component.
     */
    private val hardcodedLicenses: Map<String, Pair<String, String?>> = mapOf(
        // Example: "com.example:some-artifact" to ("MIT License" to "https://opensource.org/licenses/MIT")
    )

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputArtifact
    abstract val inputPom: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val pomFile = inputPom.get().asFile
        val outputFile = outputs.file("license-metadata.json")
        val metadata = parsePom(pomFile)
        outputFile.writeText(Gson().toJson(metadata))
    }

    private fun parsePom(pomFile: File): Map<String, String?> {
        val doc = try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
        } catch (e: Exception) {
            return emptyMap()
        }
        doc.documentElement.normalize()
        val root = doc.documentElement

        fun Node.directChildText(name: String): String? =
            childNodes.items().firstOrNull { it.nodeName == name }
                ?.textContent?.trim()?.takeIf { it.isNotBlank() }

        // groupId may be in the POM directly or inherited from the <parent> block
        val groupId = root.directChildText("groupId")
            ?: root.directChildText("parent")
                ?.let { (doc.getElementsByTagName("parent").item(0) as? Element)?.directChildText("groupId") }
            ?: return emptyMap()
        val artifactId = root.directChildText("artifactId") ?: return emptyMap()
        val version = root.directChildText("version")
            ?: (doc.getElementsByTagName("parent").item(0) as? Element)?.directChildText("version")

        // Check hardcoded overrides first
        val override = hardcodedLicenses["$groupId:$artifactId"]
        if (override != null) {
            return mapOf(
                "groupId" to groupId,
                "artifactId" to artifactId,
                "version" to version,
                "licenseName" to override.first,
                "licenseUrl" to override.second
            )
        }

        // Find the <licenses> element as a direct child of <project>
        val licensesNode = root.childNodes.items().firstOrNull { it.nodeName == "licenses" }
        val firstLicenseNode = licensesNode?.childNodes?.items()?.firstOrNull { it.nodeName == "license" }

        val licenseName = firstLicenseNode?.childNodes?.items()
            ?.firstOrNull { it.nodeName == "name" }
            ?.textContent?.trim()?.takeIf { it.isNotBlank() }

        val licenseUrl = firstLicenseNode?.childNodes?.items()
            ?.firstOrNull { it.nodeName == "url" }
            ?.textContent?.trim()?.takeIf { it.isNotBlank() }

        val parentElement = root.childNodes.items().firstOrNull { it.nodeName == "parent" } as? Element
        return mapOf(
            "groupId" to groupId,
            "artifactId" to artifactId,
            "version" to version,
            "licenseName" to licenseName,
            "licenseUrl" to licenseUrl,
            "parentGroupId" to parentElement?.directChildText("groupId"),
            "parentArtifactId" to parentElement?.directChildText("artifactId"),
            "parentVersion" to parentElement?.directChildText("version"),
        )
    }

    private fun NodeList.items(): Sequence<Node> = sequence {
        for (i in 0 until length) yield(item(i))
    }
}
