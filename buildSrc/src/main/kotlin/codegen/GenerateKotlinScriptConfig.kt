package codegen

import com.intellij.util.xmlb.XmlSerializer
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.relocated.org.jdom.Document
import org.jetbrains.kotlin.relocated.org.jdom.Element
import org.jetbrains.kotlin.relocated.org.jdom.output.Format
import org.jetbrains.kotlin.relocated.org.jdom.output.XMLOutputter
import org.jetbrains.kotlin.script.KotlinScriptConfig
import org.jetbrains.kotlin.script.loadScriptConfigs
import java.io.File
import java.io.StringWriter
import java.nio.charset.Charset

open class GenerateKotlinScriptConfig : DefaultTask() {

    @InputFile
    var template: File? = null

    @OutputFile
    var outputFile: File? = null

    @Input
    var classpath: FileCollection? = null

    @TaskAction
    fun generate() {
        outputFile!!.writeText(
            toXml(loadScriptConfigs(template!!).apply { augmentClasspathsOf(this) }),
            Charset.forName("utf-8"))
    }

    private fun augmentClasspathsOf(scriptConfigs: List<KotlinScriptConfig>) {
        val classpathElements = classpath!!.map { it.absolutePath }
        scriptConfigs.forEach {
            it.classpath.addAll(classpathElements)
        }
    }
}

fun toXml(scriptConfigs: List<KotlinScriptConfig>): String =
    prettyPrint(xmlDocumentFor(scriptConfigs))

fun xmlDocumentFor(scriptConfigs: List<KotlinScriptConfig>): Document {
    val doc = Document(Element("KotlinScriptDefinitions"))
    scriptConfigs.forEach {
        val element = XmlSerializer.serialize(it)
        doc.rootElement.addContent(element)
    }
    return doc
}

fun prettyPrint(doc: Document): String {
    val writer = StringWriter()
    with (XMLOutputter()) {
        setFormat(Format.getPrettyFormat())
        output(doc, writer)
    }
    return writer.toString()
}

