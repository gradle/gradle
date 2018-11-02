package org.gradle.gradlebuild.docs

import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.Charset.defaultCharset
import javax.inject.Inject


@CacheableTask
open class RenderMarkdownTask @Inject constructor(
    @get:PathSensitive(PathSensitivity.NONE) @get:InputFile val markdownFile: File,
    @get:OutputFile val destination: File
) : DefaultTask() {

    @Input
    val inputEncoding = defaultCharset().name()

    @Input
    val outputEncoding = defaultCharset().name()

    @TaskAction
    fun process() {
        val options = MutableDataSet().apply { set(Parser.EXTENSIONS, listOf(TablesExtension.create())) }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val markdown = markdownFile.readText(Charset.forName(inputEncoding))
        val html = renderer.render(parser.parse(markdown))
        destination.writeText(html, Charset.forName(outputEncoding))
    }
}


private
const val PEGDOWN_PARSING_TIMEOUT_MILLIS = 10_000L
