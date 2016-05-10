package codegen

import com.intellij.util.xmlb.XmlSerializer
import org.gradle.api.DefaultTask
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
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
import javax.inject.Inject

open class GenerateKotlinScriptConfig : DefaultTask() {

    @InputFile
    var template: File? = null

    @OutputFile
    var outputFile: File? = null

    @get:Input
    val classPath: List<String> by lazy { computeClassPath() }

    open val classPathRegistry: ClassPathRegistry
        @Inject get() = throw NotImplementedError()

    @TaskAction
    fun generate() {
        outputFile!!.writeText(
            toXml(loadScriptConfigs(template!!).apply { augmentClassPathsOf(this) }),
            Charset.forName("utf-8"))
    }

    private fun augmentClassPathsOf(scriptConfigs: List<KotlinScriptConfig>) {
        scriptConfigs.forEach {
            it.classpath.addAll(classPath)
        }
    }

    private fun computeClassPath() =
        gradleApi()
            .asFiles
            .filter { includeInClassPath(it.name) }
            .map { it.absolutePath }
            .sorted()

    private fun gradleApi() =
        classPathRegistry.getClassPath(DependencyFactory.ClassPathNotation.GRADLE_API.name)

    private fun includeInClassPath(name: String): Boolean {
        return name.startsWith("kotlin-stdlib-")
            || name.startsWith("kotlin-reflect-")
            || name.startsWith("ant-")
            || name.startsWith("gradle-")
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

