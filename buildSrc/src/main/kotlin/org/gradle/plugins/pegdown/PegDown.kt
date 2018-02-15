package org.gradle.plugins.pegdown

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.pegdown.PegDownProcessor
import java.io.File
import java.nio.charset.Charset

@CacheableTask
open class PegDown : DefaultTask() {

    @Input
    val inputEncoding = Charset.defaultCharset()

    @Input
    val outputEncoding = Charset.defaultCharset()

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    lateinit var markdownFile: File

    @OutputFile
    lateinit var destination: File

    @TaskAction
    fun process() {
        val processor = PegDownProcessor(0)
        val markdown = markdownFile.readText(inputEncoding)
        val html = processor.markdownToHtml(markdown)
        destination.writeText(html, outputEncoding)
    }
}
