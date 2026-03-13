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

    /** Coordinates of a Maven `<parent>` declaration inside a POM. */
    data class ParentCoordinates(val groupId: String, val artifactId: String, val version: String)

    data class PomInfo(
        val groupId: String,
        val artifactId: String,
        val version: String?,
        val licenseName: String?,
        val licenseUrl: String?,
        /** Non-null only when the POM declares a `<parent>` block with complete coordinates. */
        val parent: ParentCoordinates?,
    )

    /**
     * Parses [pomFile] and returns a [PomInfo], or `null` if the file cannot be parsed or
     * lacks the mandatory `groupId` / `artifactId` coordinates.
     */
    fun parsePom(pomFile: File): PomInfo? {
        val doc = runCatching {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
        }.getOrNull() ?: return null
        doc.documentElement.normalize()
        val root = doc.documentElement

        fun Node.directChildText(name: String): String? =
            childNodes.asSequence().firstOrNull { it.nodeName == name }
                ?.textContent?.trim()?.takeIf { it.isNotBlank() }

        val parentElement = root.childNodes.asSequence().firstOrNull { it.nodeName == "parent" } as? Element

        // groupId may be declared directly or inherited from <parent>
        val groupId = root.directChildText("groupId") ?: parentElement?.directChildText("groupId") ?: return null
        val artifactId = root.directChildText("artifactId") ?: return null
        val version = root.directChildText("version") ?: parentElement?.directChildText("version")

        val firstLicense = root.childNodes.asSequence()
            .firstOrNull { it.nodeName == "licenses" }
            ?.childNodes?.asSequence()
            ?.firstOrNull { it.nodeName == "license" }
        val licenseName = firstLicense?.childNodes?.asSequence()
            ?.firstOrNull { it.nodeName == "name" }
            ?.textContent?.trim()?.takeIf { it.isNotBlank() }
        val licenseUrl = firstLicense?.childNodes?.asSequence()
            ?.firstOrNull { it.nodeName == "url" }
            ?.textContent?.trim()?.takeIf { it.isNotBlank() }

        val parent = if (parentElement != null) {
            val pg = parentElement.directChildText("groupId")
            val pa = parentElement.directChildText("artifactId")
            val pv = parentElement.directChildText("version")
            if (pg != null && pa != null && pv != null) ParentCoordinates(pg, pa, pv) else null
        } else {
            null
        }

        return PomInfo(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            licenseName = licenseName,
            licenseUrl = licenseUrl,
            parent = parent,
        )
    }

    private fun NodeList.asSequence(): Sequence<Node> = (0 until length).asSequence().map(::item)
}
