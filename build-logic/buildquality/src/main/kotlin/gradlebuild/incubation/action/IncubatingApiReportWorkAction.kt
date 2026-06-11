/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.incubation.action

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.body.AnnotationDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.comments.JavadocComment
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName
import com.github.javaparser.javadoc.Javadoc
import com.github.javaparser.javadoc.description.JavadocSnippet
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import gradlebuild.basics.util.KotlinSourceParser
import org.gradle.workers.WorkAction
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffsetSkippingComments
import java.io.File
import java.nio.file.Path


abstract class IncubatingApiReportWorkAction : WorkAction<IncubatingApiReportParameter> {

    override fun execute() {
        try {
            val versionToIncubating = mutableMapOf<Version, MutableSet<IncubatingDescription>>()
            val repositoryRoot = parameters.repositoryRoot.get().asFile.toPath()
            parameters.srcDirs.forEach { srcDir ->
                if (srcDir.exists()) {
                    val collector = CompositeVersionsToIncubatingCollector(
                        listOf(
                            JavaVersionsToIncubatingCollector(srcDir, repositoryRoot = repositoryRoot),
                            KotlinVersionsToIncubatingCollector(repositoryRoot = repositoryRoot)
                        )
                    )
                    srcDir.walkTopDown().forEach { sourceFile ->
                        try {
                            collector.collectFrom(sourceFile).forEach { (version, incubating) ->
                                versionToIncubating.getOrPut(version) { mutableSetOf() }.addAll(incubating)
                            }
                        } catch (e: Exception) {
                            throw Exception("Unable to parse $sourceFile", e)
                        }
                    }
                }
            }
            generateTextReport(versionToIncubating)
            generateHtmlReport(versionToIncubating)
        } finally {
            JavaParserFacade.clearInstances()
        }
    }

    private
    fun generateHtmlReport(versionToIncubating: VersionsToIncubating) {
        val htmlReport = parameters.htmlReportFile.get().asFile
        val title = parameters.title.get()
        htmlReport.parentFile.mkdirs()
        htmlReport.printWriter(Charsets.UTF_8).use { writer ->
            writer.println(
                """<html lang="en">
    <head>
       <META http-equiv="Content-Type" content="text/html; charset=UTF-8">
       <title>Incubating APIs for $title</title>
       <link xmlns:xslthl="http://xslthl.sf.net" rel="stylesheet" href="https://fonts.googleapis.com/css?family=Lato:400,400i,700">
       <meta xmlns:xslthl="http://xslthl.sf.net" content="width=device-width, initial-scale=1" name="viewport">
       <link xmlns:xslthl="http://xslthl.sf.net" type="text/css" rel="stylesheet" href="https://docs.gradle.org/current/userguide/base.css">

    </head>
    <body>
       <h1>Incubating APIs for  $title</h1>
    """
            )
            val versions = versionsDates()
            versionToIncubating.toSortedMap().forEach { (version, incubatingDescriptions) ->
                writer.println("<a name=\"sec_$version\"></a>")
                writer.println(
                    "<h2>Incubating since $version (${versions[version]?.run { "released on $this" } ?: "unreleased"})</h2>"
                )
                writer.println("<ul>")
                incubatingDescriptions.sortedBy { it.name }.forEach { incubating ->
                    writer.println("   <li>${incubating.name.escape()}</li>")
                }
                writer.println("</ul>")
            }
            writer.println("</body></html>")
        }
    }

    private
    fun generateTextReport(versionToIncubating: VersionsToIncubating) {
        val textReport = parameters.textReportFile.get().asFile
        textReport.parentFile.mkdirs()
        textReport.printWriter(Charsets.UTF_8).use { writer ->
            val versions = versionsDates()
            versionToIncubating.toSortedMap().forEach { (version, incubatingDescriptions) ->
                val releaseDate = versions[version] ?: "unreleased"
                incubatingDescriptions.sortedBy { it.name }.forEach { incubating ->
                    writer.println("$version;$releaseDate;${incubating.kind};${incubating.name};${incubating.sourceRelativePath};${incubating.lineNumber}")
                }
            }
        }
    }

    private
    fun versionsDates(): Map<Version, String> =
        ReleasedVersions.versionDates(parameters.releasedVersionsFile.get().asFile)


    private
    fun String.escape() = replace("<", "&lt;").replace(">", "&gt;")
}


private
typealias Version = String


private
data class IncubatingDescription(val name: String, val kind: IncubatingApiKind, val sourceRelativePath: Path, val lineNumber: Int)


private
typealias VersionsToIncubating = Map<Version, MutableSet<IncubatingDescription>>


private
interface VersionsToIncubatingCollector {

    fun collectFrom(sourceFile: File): VersionsToIncubating
}


private
class CompositeVersionsToIncubatingCollector(

    private
    val collectors: List<VersionsToIncubatingCollector>

) : VersionsToIncubatingCollector {

    override fun collectFrom(sourceFile: File): VersionsToIncubating =
        collectors
            .flatMap { it.collectFrom(sourceFile).entries }
            .associate { it.key to it.value }
}


private
const val VERSION_NOT_FOUND = "Not found"


private
class JavaVersionsToIncubatingCollector(srcDir: File, val repositoryRoot: Path) : VersionsToIncubatingCollector {

    private
    val solver = JavaSymbolSolver(CombinedTypeSolver(JavaParserTypeSolver(srcDir), ReflectionTypeSolver()))

    override fun collectFrom(sourceFile: File): VersionsToIncubating {

        if (!sourceFile.name.endsWith(".java")) return emptyMap()

        val versionsToIncubating = mutableMapOf<Version, MutableSet<IncubatingDescription>>()
        JavaParser().parse(sourceFile).getResult().get().run {
            solver.inject(this)
            findAllIncubating()
                .map { node ->
                    toVersionIncubating(sourceFile, node) }
                .forEach { (version, incubating) ->
                    versionsToIncubating.getOrPut(version) { mutableSetOf() }.add(incubating)
                }
        }
        return versionsToIncubating
    }

    private
    fun CompilationUnit.findAllIncubating() =
        findAll(Node::class.java).filter { it.isIncubating }

    private
    val Node.isIncubating: Boolean
        get() = (this as? NodeWithAnnotations<*>)?.annotations?.any { it.nameAsString == "Incubating" } ?: false

    private
    fun CompilationUnit.toVersionIncubating(sourceFile: File, node: Node) =
        Pair(
            (node as? NodeWithJavadoc<*>)?.javadoc?.orElse(null)?.let { findVersionFromJavadoc(it) }
                // This is needed to find the JavaDoc of a package declaration in 'package-info.java'
                ?: (node as? PackageDeclaration)?.parentNode?.get()?.childNodes?.filterIsInstance<JavadocComment>()?.singleOrNull()?.parse()?.let { findVersionFromJavadoc(it) }
                ?: VERSION_NOT_FOUND,
            toIncubatingDescription(node, this, sourceFile)
        )

    private
    fun toIncubatingDescription(node: Node, unit: CompilationUnit, file: File): IncubatingDescription {
        val nodeName = nodeName(node, unit, file)
        return IncubatingDescription(
            name = nodeName,
            kind = kindOf(node),
            sourceRelativePath = repositoryRoot.relativize(file.toPath()),
            lineNumber = node.begin.map { it.line }.orElse(-1)
        )
    }

    private
    fun kindOf(node: Node): IncubatingApiKind = when (node) {
        is EnumDeclaration -> IncubatingApiKind.ENUM
        is AnnotationDeclaration -> IncubatingApiKind.ANNOTATION
        is ClassOrInterfaceDeclaration -> if (node.isInterface) IncubatingApiKind.INTERFACE else IncubatingApiKind.CLASS
        is MethodDeclaration -> IncubatingApiKind.METHOD
        is FieldDeclaration -> IncubatingApiKind.FIELD
        is PackageDeclaration -> IncubatingApiKind.PACKAGE
        else -> IncubatingApiKind.OTHER
    }

    private
    fun findVersionFromJavadoc(javadoc: Javadoc): String? =
        javadoc.blockTags
            .find { tag -> tag.tagName == "since" }
            ?.content?.elements?.find { description -> description is JavadocSnippet }
            ?.toText()

    private
    fun nodeName(it: Node?, unit: CompilationUnit, file: File) = when (it) {
        is EnumDeclaration -> tryResolve({ it.resolve().qualifiedName }) { inferClassName(unit) }
        is ClassOrInterfaceDeclaration -> tryResolve({ it.resolve().qualifiedName }) { inferClassName(unit) }
        is MethodDeclaration -> tryResolve({ it.resolve().qualifiedSignature }) { "${inferClassName(unit)}.${it.name}()" }
        is AnnotationDeclaration -> tryResolve({ it.resolve().qualifiedName }) { "${inferClassName(unit)}.${it.name}" }
        is FieldDeclaration -> tryResolve({ "${inferClassName(unit)}.${it.resolve().name}" }) { inferClassName(unit) }
        is PackageDeclaration -> "${it.nameAsString} (package-info.java)"
        is NodeWithSimpleName<*> -> "${inferClassName(unit)}.${it.nameAsString}"
        else -> unit.primaryTypeName.orElse(file.name)
    }

    private
    fun inferClassName(unit: CompilationUnit) =
        "${unit.packageDeclaration.map { it.nameAsString }.orElse("")}.${unit.primaryTypeName.orElse("")}"

    private
    inline fun tryResolve(resolver: () -> String, or: () -> String) = try {
        resolver()
    } catch (_: Throwable) {
        or()
    }
}


private
val NEWLINE_REGEX = "\\n\\s*".toRegex()


private
class KotlinVersionsToIncubatingCollector(val repositoryRoot: Path) : VersionsToIncubatingCollector {

    override fun collectFrom(sourceFile: File): VersionsToIncubating {

        if (!sourceFile.name.endsWith(".kt")) return emptyMap()

        val versionsToIncubating = mutableMapOf<Version, MutableSet<IncubatingDescription>>()
        KotlinSourceParser().mapParsedKotlinFiles(sourceFile) { ktFile ->
            ktFile.forEachIncubatingDeclaration { declaration ->
                versionsToIncubating
                    .getOrPut(declaration.sinceVersion) { mutableSetOf() }
                    .add(buildIncubatingDescription(sourceFile, ktFile, declaration))
            }
        }
        return versionsToIncubating
    }

    private
    fun buildIncubatingDescription(sourceFile: File, ktFile: KtFile, declaration: KtNamedDeclaration): IncubatingDescription {
        var incubating = "${declaration.typeParametersString}${declaration.fullyQualifiedName}"
        if (declaration is KtCallableDeclaration) {
            incubating += declaration.valueParametersString
            declaration.receiverTypeString?.let { receiver ->
                incubating += " with $receiver receiver"
            }
            if (declaration.parent == ktFile) {
                incubating += ", top-level in ${sourceFile.name}"
            }
        }
        return IncubatingDescription(
            name = incubating.replace(NEWLINE_REGEX, " "),
            kind = kindOf(declaration),
            sourceRelativePath = repositoryRoot.relativize(sourceFile.toPath()),
            lineNumber = getLineNumber(declaration, ktFile)
        )
    }

    private
    fun kindOf(declaration: KtNamedDeclaration): IncubatingApiKind = when {
        declaration is KtClass && declaration.isInterface() -> IncubatingApiKind.INTERFACE
        declaration is KtClass && declaration.isEnum() -> IncubatingApiKind.ENUM
        declaration is KtClass && declaration.isAnnotation() -> IncubatingApiKind.ANNOTATION
        declaration is KtClass -> IncubatingApiKind.CLASS
        declaration is KtObjectDeclaration -> IncubatingApiKind.CLASS
        declaration is KtConstructor<*> -> IncubatingApiKind.METHOD
        declaration is KtNamedFunction -> IncubatingApiKind.METHOD
        declaration is KtProperty -> IncubatingApiKind.PROPERTY
        declaration is KtParameter -> IncubatingApiKind.PROPERTY
        else -> IncubatingApiKind.OTHER
    }

    private
    fun getLineNumber(declaration: KtDeclaration, ktFile: KtFile): Int {
        val startOffset = declaration.startOffsetSkippingComments
        return ktFile.viewProvider.document?.getLineNumber(startOffset)?.plus(1) ?: -1
    }

    private
    fun KtFile.forEachIncubatingDeclaration(block: (KtNamedDeclaration) -> Unit) {
        collectDescendantsOfType<KtNamedDeclaration>().filter { it.isIncubating }.forEach(block)
    }

    private
    val KtNamedDeclaration.isIncubating: Boolean
        get() = annotationEntries.any { it.shortName?.asString() == "Incubating" }

    private
    val KtNamedDeclaration.typeParametersString: String
        get() = (this as? KtCallableDeclaration)?.typeParameterList?.let { "${it.text} " } ?: ""

    private
    val KtNamedDeclaration.fullyQualifiedName: String
        get() = fqName!!.asString()

    private
    val KtCallableDeclaration.valueParametersString: String
        get() = valueParameterList?.text ?: ""

    private
    val KtCallableDeclaration.receiverTypeString: String?
        get() = receiverTypeReference?.text

    private
    val KtNamedDeclaration.sinceVersion: String
        get() = docComment?.getDefaultSection()?.findTagsByName("since")?.singleOrNull()?.getContent()
            ?: VERSION_NOT_FOUND
}


