/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin

import org.gradle.script.lang.kotlin.KotlinScriptDefinitionProvider.scriptDefinitionFor
import org.gradle.script.lang.kotlin.KotlinScriptDefinitionProvider.selectGradleApiJars

import org.gradle.api.DefaultTask

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.api.internal.ClassPathRegistry

import org.jetbrains.kotlin.relocated.org.jdom.Document
import org.jetbrains.kotlin.relocated.org.jdom.Element
import org.jetbrains.kotlin.relocated.org.jdom.output.Format
import org.jetbrains.kotlin.relocated.org.jdom.output.XMLOutputter
import org.jetbrains.kotlin.script.KotlinConfigurableScriptDefinition

import com.intellij.util.xmlb.XmlSerializer

import java.io.File
import java.io.StringWriter
import javax.inject.Inject

open class GenerateKtsConfig : DefaultTask() {

    override fun getGroup() = "IDE"

    override fun getDescription() =
        "Generates the Kotlin Script configuration file at ${project.relativePath(effectiveOutputFile)}."

    var outputFile: File? = null

    @get:OutputFile
    val effectiveOutputFile: File by lazy {
        outputFile ?: defaultOutputFile()
    }

    @get:Input
    val classPath: List<File> by lazy {
        computeClassPath()
    }

    @get:Inject
    open val classPathRegistry: ClassPathRegistry
        get() = throw NotImplementedError()

    @TaskAction
    fun generate() =
        effectiveOutputFile.writeText(
            toXml(scriptDefinitionFor(classPath)))

    private fun defaultOutputFile() =
        project.file("gradle.ktscfg.xml")

    private fun computeClassPath() =
        selectGradleApiJars(classPathRegistry)
            // speed up IDE completion by removing jars
            // already included in the gradle-ide-support library by
            // PatchIdeaConfig
            .filter { !it.name.startsWith("gradle-") }
            .sortedBy { it.name }
}

private
fun toXml(scriptDefinition: KotlinConfigurableScriptDefinition) =
    prettyPrint(xmlDocumentFor(scriptDefinition))

private
fun xmlDocumentFor(scriptDefinition: KotlinConfigurableScriptDefinition): Document {
    val doc = Document(Element("KotlinScriptDefinitions"))
    scriptDefinition.config.let {
        val element = XmlSerializer.serialize(it)
        doc.rootElement.addContent(element)
    }
    return doc
}

internal
fun prettyPrint(doc: Document): String {
    val writer = StringWriter()
    with (XMLOutputter()) {
        setFormat(Format.getPrettyFormat())
        output(doc, writer)
    }
    return writer.toString()
}
