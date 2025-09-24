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

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.internal.util.PropertiesUtils
import org.gradle.work.DisableCachingByDefault
import org.w3c.dom.Element
import java.util.Properties
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

@DisableCachingByDefault(because = "Not worth tracking")
abstract class AbstractVersionsUpdateTask : DefaultTask() {

    @get:Internal
    abstract val comment: Property<String>

    @get:Internal
    abstract val propertiesFile: RegularFileProperty

    protected
    fun updateCompatibilityDoc(
        compatibilityDocFile: RegularFileProperty,
        linePrefix: String,
        firstVersion: String,
        latestVersion: String,
    ) {
        val docFile = compatibilityDocFile.get().asFile
        var lineFound = false
        docFile.writeText(
            docFile.readLines().joinToString(separator = "\n", postfix = "\n") { line ->
                if (line.startsWith(linePrefix)) {
                    lineFound = true
                    "$linePrefix ${firstVersion} through ${latestVersion}."
                } else {
                    line
                }
            }
        )
        require(lineFound) {
            "File '$docFile' does not contain the expected compatibility line: '$linePrefix'"
        }
    }

    protected
    fun fetchVersionsFromMavenMetadata(url: String): List<String> =
        DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }.newDocumentBuilder()
            .parse(url)
            .getElementsByTagName("version").let { versions ->
                (0 until versions.length)
                    .map { idx -> (versions.item(idx) as Element).textContent }
                    .reversed()
            }

    protected
    fun updateProperties(populateProperties: Properties.() -> Unit) =
        PropertiesUtils.store(
            Properties().apply(populateProperties),
            propertiesFile.get().asFile,
            comment.get(),
            Charsets.ISO_8859_1,
            "\n"
        )
}
