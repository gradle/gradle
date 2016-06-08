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

package org.gradle.script.lang.kotlin.tasks

import org.jetbrains.kotlin.com.intellij.openapi.util.JDOMUtil.loadDocument
import org.gradle.api.DefaultTask
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.script.lang.kotlin.support.KotlinScriptDefinitionProvider.selectGradleApiJars
import org.jetbrains.kotlin.org.jdom.Document
import org.jetbrains.kotlin.org.jdom.Element
import java.io.File
import javax.inject.Inject

open class PatchIdeaConfig : DefaultTask() {

    override fun getGroup() = "IDE"

    override fun getDescription() =
        "Patches IDEA config files."

    @get:Inject
    open val classPathRegistry: ClassPathRegistry
        get() = throw NotImplementedError()

    val libraryName = "gradle-ide-support"

    @get:OutputFile
    val outputFile: File by lazy {
        project.file(".idea/libraries/${libraryName.replace('-', '_')}.xml")
    }

    @get:OutputFile
    val moduleFile: File by lazy {
        project.file(".idea/modules/${project.name}.iml")
    }

    @get:Input
    val classPath: List<File> by lazy {
        computeClassPath()
    }

    @TaskAction
    fun generate() {
        writeGradleIdeSupportLibrary()
        patchProjectModule()
    }

    private fun writeGradleIdeSupportLibrary() {
        outputFile.writeText(
            prettyPrint(
                libraryDocumentFor(libraryName, gradleIdeSupportLibraryJars())))
    }

    private fun gradleIdeSupportLibraryJars(): List<File> {
        val gradleJars = classPath.filter {
            it.name.startsWith("gradle-")
        }
        val (kotlinJar, coreJars) = gradleJars.partition {
            it.name.startsWith("gradle-script-kotlin")
        }
        return coreJars + kotlinJar.map { relocate(it) }
    }

    private fun relocate(file: File): File {
        val relocatedFile = File(tmpDir(), file.name)
        file.copyTo(relocatedFile, true)
        return relocatedFile
    }

    private fun tmpDir() = System.getProperty("java.io.tmpdir")

    private fun patchProjectModule() {
        val module = loadDocument(moduleFile)
        addLibraryEntryTo(module, libraryName)
        moduleFile.writeText(prettyPrint(module))
    }

    private fun addLibraryEntryTo(module: Document, libraryName: String) {
        val component = module.rootElement.getChild("component")
        if (hasLibraryEntry(component, libraryName))
            return
        component.addContent(
            // <orderEntry type="library" name="$LIBRARY_NAME" level="project" />
            Element("orderEntry").apply {
                setAttribute("type", "library")
                setAttribute("name", libraryName)
                setAttribute("level", "project")
            })
    }

    private fun hasLibraryEntry(component: Element, libraryName: String) =
        component.getChildren("orderEntry").any { it.getAttributeValue("name") == libraryName }

    private fun libraryDocumentFor(libraryName: String, jars: List<File>): Document {
        /*
        <component name="libraryTable">
          <library name="$LIBRARY_NAME">
            <CLASSES>
              <root url="jar://$JAR!/" />
            </CLASSES>
            <JAVADOC />
            <SOURCES>
              <root url="jar://$JAR!/" />
            </SOURCES>
          </library>
        </component>
        */
        val classes = Element("CLASSES")
        val sources = Element("SOURCES")
        jars.forEach { jar ->
            classes.addContent(rootElementFor(jar))
            sources.addContent(rootElementFor(jar))
        }

        val component = Element("component").apply {
            setAttribute("name", "libraryTable")
            addContent(Element("library").apply {
                setAttribute("name", libraryName)
                addContent(classes)
                addContent(Element("JAVADOC"))
                addContent(sources)
            })
        }
        return Document(component)
    }

    private fun rootElementFor(jar: File) =
        Element("root").apply {
            setAttribute("url", "jar://${jar.absolutePath}!/")
        }

    private fun computeClassPath() =
        selectGradleApiJars(classPathRegistry)
            .sortedBy { it.name }
}
