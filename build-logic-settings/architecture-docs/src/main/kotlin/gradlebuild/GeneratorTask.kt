/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.PrintWriter

abstract class GeneratorTask : DefaultTask() {
    private val markerComment = "<!-- This diagram is generated. Use `./gradlew :architectureDoc` to update it -->"
    private val startDiagram = "```mermaid"
    private val endDiagram = "```"

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val elements: ListProperty<ArchitectureElement>

    @TaskAction
    fun generate() {
        val markdownFile = outputFile.asFile.get()
        val head = if (markdownFile.exists()) {
            val content = markdownFile.readText().lines()
            val markerPos = content.indexOfFirst { it.contains(markerComment) }
            require(markerPos >= 0) { "Could not locate the generated diagram in $markdownFile" }
            val endPos = content.subList(markerPos, content.size).indexOfFirst { it.contains(endDiagram) && !it.contains(startDiagram) }
            require(endPos >= 0) { "Could not locate the end of the generated diagram in $markdownFile" }
            content.subList(0, markerPos)
        } else {
            emptyList()
        }

        markdownFile.bufferedWriter().use {
            PrintWriter(it).run {
                for (line in head) {
                    println(line)
                }
                graph(elements.get())
            }
        }
    }

    private fun PrintWriter.graph(elements: List<ArchitectureElement>) {
        println(
            """
            $markerComment
            $startDiagram
        """.trimIndent()
        )
        val writer = NodeWriter(this, "    ")
        writer.node("graph TD")
        for (element in elements) {
            if (element is Platform) {
                writer.platform(element)
            } else {
                writer.element(element)
            }
        }
        println(endDiagram)
    }

    private fun NodeWriter.platform(platform: Platform) {
        println()
        node("subgraph ${platform.id}[\"${platform.name} platform\"]") {
            for (child in platform.children) {
                element(child)
            }
        }
        node("end")
        node("style ${platform.id} fill:#c2e0f4,stroke:#3498db,stroke-width:2px,color:#000;")
        for (dep in platform.uses) {
            node("${platform.id} --> $dep")
        }
    }

    private fun NodeWriter.element(element: ArchitectureElement) {
        println()
        node("${element.id}[\"${element.name} module\"]")
        node("style ${element.id} stroke:#1abc9c,fill:#b1f4e7,stroke-width:2px,color:#000;")
    }

    private class NodeWriter(private val writer: PrintWriter, private val indent: String) {
        fun println() {
            writer.println()
        }

        fun node(node: String) {
            writer.print(indent)
            writer.println(node)
        }

        fun node(node: String, builder: NodeWriter.() -> Unit) {
            writer.print(indent)
            writer.println(node)
            builder(NodeWriter(writer, "$indent    "))
        }
    }
}
