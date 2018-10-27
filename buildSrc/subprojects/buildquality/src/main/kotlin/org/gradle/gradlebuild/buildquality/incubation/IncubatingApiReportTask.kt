package org.gradle.gradlebuild.buildquality.incubation

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.AnnotationDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName
import com.github.javaparser.javadoc.description.JavadocSnippet
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject


@CacheableTask
open class IncubatingApiReportTask
    @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    val versionFile: RegularFileProperty = project.objects.fileProperty()

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    val releasedVersionsFile: RegularFileProperty = project.objects.fileProperty()

    @Input
    val title: Property<String> = project.objects.property(String::class.java).also {
        it.set(project.provider { project.name })
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    val sources: ConfigurableFileCollection = project.files()

    @OutputFile
    val htmlReportFile: RegularFileProperty = project.objects.fileProperty().also {
        it.set(project.layout.buildDirectory.file("reports/incubating.html"))
    }

    @OutputFile
    val textReportFile: RegularFileProperty = project.objects.fileProperty().also {
        it.set(project.layout.buildDirectory.file("reports/incubating.txt"))
    }

    @TaskAction
    fun analyze() = workerExecutor.submit(Analyzer::class.java) {
        isolationMode = IsolationMode.NONE
        params(versionFile.asFile.get(), sources.files, htmlReportFile.asFile.get(), textReportFile.asFile.get(), title.get(), releasedVersionsFile.asFile.get())
    }
}


open
class Analyzer @Inject constructor(
    private val versionFile: File,
    private val srcDirs: Set<File>,
    private val htmlReportFile: File,
    private val textReportFile: File,
    private val title: String,
    private val releasedVersionsFile: File
) : Runnable {

    override
    fun run() {
        val versionToIncubating = mutableMapOf<String, MutableSet<String>>()
        srcDirs.forEach { srcDir ->
            if (srcDir.exists()) {
                val solver = JavaSymbolSolver(CombinedTypeSolver(JavaParserTypeSolver(srcDir), ReflectionTypeSolver()))
                srcDir.walkTopDown().forEach {
                    if (it.name.endsWith(".java")) {
                        try {
                            parseJavaFile(it, versionToIncubating, solver)
                        } catch (e: Exception) {
                            println("Unable to parse $it: ${e.message}")
                        }
                    }
                }
            }
        }
        generateTextReport(versionToIncubating)
        generateHtmlReport(versionToIncubating)
    }

    private
    fun parseJavaFile(file: File, versionToIncubating: MutableMap<String, MutableSet<String>>, solver: JavaSymbolSolver) =
        JavaParser.parse(file).run {
            solver.inject(this)
            findAll(Node::class.java)
                .filter {
                    it is NodeWithAnnotations<*> &&
                        it.annotations.any { it.nameAsString == "Incubating" }
                }.map {
                    val node = it as NodeWithAnnotations<*>
                    val version = findVersionFromJavadoc(node)
                        ?: "Not found"
                    Pair(version, nodeName(it, this, file))
                }.forEach {
                    versionToIncubating.getOrPut(it.first) {
                        mutableSetOf()
                    }.add(it.second)
                }
        }


    private
    fun findVersionFromJavadoc(node: NodeWithAnnotations<*>): String? = if (node is NodeWithJavadoc<*>) {
        node.javadoc.map {
            it.blockTags.find {
                it.tagName == "since"
            }?.content?.elements?.find {
                it is JavadocSnippet
            }?.toText()
        }.orElse(null)
    } else {
        null
    }

    private
    fun nodeName(it: Node?, unit: CompilationUnit, file: File) = when (it) {
        is EnumDeclaration -> tryResolve({ it.resolve().qualifiedName }) { inferClassName(unit) }
        is ClassOrInterfaceDeclaration -> tryResolve({ it.resolve().qualifiedName }) { inferClassName(unit) }
        is MethodDeclaration -> tryResolve({ it.resolve().qualifiedSignature }) { inferClassName(unit) }
        is AnnotationDeclaration -> tryResolve({ it.resolve().qualifiedName }) { inferClassName(unit) }
        is NodeWithSimpleName<*> -> it.nameAsString
        else -> unit.primaryTypeName.orElse(file.name)
    }

    private
    fun inferClassName(unit: CompilationUnit) = "${unit.packageDeclaration.map { it.nameAsString }.orElse("")}.${unit.primaryTypeName.orElse("")}"

    private
    inline fun tryResolve(resolver: () -> String, or: () -> String) = try {
        resolver()
    } catch (e: Throwable) {
        or()
    }

    private
    fun generateHtmlReport(versionToIncubating: Map<String, Set<String>>) {
        htmlReportFile.parentFile.mkdirs()
        htmlReportFile.printWriter(Charsets.UTF_8).use { writer ->
            writer.println("""<html lang="en">
    <head>
       <META http-equiv="Content-Type" content="text/html; charset=UTF-8">
       <title>Incubating APIs for $title</title>
       <link xmlns:xslthl="http://xslthl.sf.net" rel="stylesheet" href="https://fonts.googleapis.com/css?family=Lato:400,400i,700">
       <meta xmlns:xslthl="http://xslthl.sf.net" content="width=device-width, initial-scale=1" name="viewport">
       <link xmlns:xslthl="http://xslthl.sf.net" type="text/css" rel="stylesheet" href="https://docs.gradle.org/current/userguide/base.css">

    </head>
    <body>
       <h1>Incubating APIs for $title</h1>
    """)
            val versions = versionsDates()
            versionToIncubating.toSortedMap().forEach {
                writer.println("<a name=\"sec_${it.key}\"></a>")
                writer.println("<h2>Incubating since ${it.key} (${versions.get(it.key)?.run { "released on $this" }
                    ?: "unreleased"})</h2>")
                writer.println("<ul>")
                it.value.sorted().forEach {
                    writer.println("   <li>${it.escape()}</li>")
                }
                writer.println("</ul>")
            }
            writer.println("</body></html>")
        }
    }

    private
    fun generateTextReport(versionToIncubating: Map<String, Set<String>>) {
        textReportFile.parentFile.mkdirs()
        textReportFile.printWriter(Charsets.UTF_8).use { writer ->
            val versions = versionsDates()
            versionToIncubating.toSortedMap().forEach {
                val version = it.key
                val releaseDate = versions.get(it.key) ?: "unreleased"
                it.value.sorted().forEach {
                    writer.println("$version;$releaseDate;$it")
                }
            }
        }
    }

    private
    fun versionsDates(): Map<String, String> {
        val versions = mutableMapOf<String, String>()
        var version: String? = null
        releasedVersionsFile.forEachLine(Charsets.UTF_8) {
            val line = it.trim()
            if (line.startsWith("\"version\"")) {
                version = line.substring(line.indexOf("\"", 11) + 1, line.lastIndexOf("\""))
            }
            if (line.startsWith("\"buildTime\"")) {
                var date = line.substring(line.indexOf("\"", 12) + 1, line.lastIndexOf("\""))
                date = date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8)
                versions.put(version!!, date)
            }
        }
        return versions
    }


    private
    fun String.escape() = replace("<", "&lt;").replace(">", "&gt;")
}
