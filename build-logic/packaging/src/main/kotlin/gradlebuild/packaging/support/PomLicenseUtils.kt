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

package gradlebuild.packaging.support

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory


/**
 * Shared utility for parsing Maven POM files to extract license and parent metadata.
 *
 * Used both at configuration time (to discover parent POM coordinates for pre-resolution)
 * and at task execution time (to extract license names from the pre-resolved POM files).
 */
object PomLicenseUtils {

    data class PomInfo(
        val groupId: String?,
        val artifactId: String?,
        val version: String?,
        val licenseName: String?,
        val licenseUrl: String?,
        val parentGroupId: String?,
        val parentArtifactId: String?,
        val parentVersion: String?,
    )

    fun parsePom(pomFile: File): PomInfo {
        val doc = try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
        } catch (e: Exception) {
            return PomInfo(null, null, null, null, null, null, null, null)
        }
        doc.documentElement.normalize()
        val root = doc.documentElement

        fun Node.directChildText(name: String): String? =
            childNodes.items().firstOrNull { it.nodeName == name }
                ?.textContent?.trim()?.takeIf { it.isNotBlank() }

        val parentElement = root.childNodes.items().firstOrNull { it.nodeName == "parent" } as? Element

        // groupId may be declared directly or inherited from <parent>
        val groupId = root.directChildText("groupId") ?: parentElement?.directChildText("groupId")
        val artifactId = root.directChildText("artifactId")
        val version = root.directChildText("version") ?: parentElement?.directChildText("version")

        val licensesNode = root.childNodes.items().firstOrNull { it.nodeName == "licenses" }
        val firstLicense = licensesNode?.childNodes?.items()?.firstOrNull { it.nodeName == "license" }
        val licenseName = firstLicense?.childNodes?.items()
            ?.firstOrNull { it.nodeName == "name" }
            ?.textContent?.trim()?.takeIf { it.isNotBlank() }
        val licenseUrl = firstLicense?.childNodes?.items()
            ?.firstOrNull { it.nodeName == "url" }
            ?.textContent?.trim()?.takeIf { it.isNotBlank() }

        return PomInfo(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            licenseName = licenseName,
            licenseUrl = licenseUrl,
            parentGroupId = parentElement?.directChildText("groupId"),
            parentArtifactId = parentElement?.directChildText("artifactId"),
            parentVersion = parentElement?.directChildText("version"),
        )
    }

    private fun NodeList.items(): Sequence<Node> = sequence {
        for (i in 0 until length) yield(item(i))
    }
}
