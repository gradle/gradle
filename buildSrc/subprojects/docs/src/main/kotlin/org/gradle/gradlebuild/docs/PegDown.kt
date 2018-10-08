package org.gradle.gradlebuild.docs

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.pegdown.PegDownProcessor

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.Charset.defaultCharset
import javax.inject.Inject


@CacheableTask
open class PegDown @Inject constructor(
    @get:PathSensitive(PathSensitivity.NONE) @get:InputFile val markdownFile: File,
    @get:OutputFile val destination: File
) : DefaultTask() {

    @Input
    val inputEncoding = defaultCharset().name()

    @Input
    val outputEncoding = defaultCharset().name()

    @TaskAction
    fun process() {
        val processor = PegDownProcessor(0)
        val markdown = markdownFile.readText(Charset.forName(inputEncoding))
        val html = processor.markdownToHtml(markdown)
        destination.writeText(html, Charset.forName(outputEncoding))
    }
}
