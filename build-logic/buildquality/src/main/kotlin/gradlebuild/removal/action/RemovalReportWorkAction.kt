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

package gradlebuild.removal.action

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.IntegerLiteralExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import gradlebuild.basics.util.KotlinSourceParser
import org.gradle.workers.WorkAction
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File
import java.nio.file.Path


/**
 * A single discovered deprecation that is scheduled for removal / to become an error.
 */
data class RemovalFinding(
    val timeline: RemovalTimeline,
    val kind: RemovalKind,
    /** The deprecated symbol (verbatim) or `<dynamic: ...>` when it can't be resolved statically. */
    val symbol: String,
    /** Upgrade guide major version, or null when the chain uses no `withUpgradeGuideSection(...)`. */
    val guideMajor: Int?,
    /** Upgrade guide section id, or null when not documented via the upgrade guide. */
    val guideSection: String?,
    val sourceRelativePath: Path,
    val lineNumber: Int
)


/**
 * Scans `main` sources for the [RemovalTimeline] marker methods, walks each deprecation fluent chain
 * to recover the deprecated symbol and the upgrade-guide section, and writes per-project txt + html
 * reports. Modeled on the incubating-API report.
 */
abstract class RemovalReportWorkAction : WorkAction<RemovalReportParameter> {

    override fun execute() {
        val repositoryRoot = parameters.repositoryRoot.get().asFile.toPath()
        val targetMajor = parameters.targetMajorVersion.get()
        val markers = RemovalTimeline.markersByMethod(targetMajor)
        val findings = sortedSetOf(FINDING_ORDER)
        parameters.srcDirs.forEach { srcDir ->
            if (srcDir.exists()) {
                val java = JavaRemovalCollector(repositoryRoot, markers)
                val kotlin = KotlinRemovalCollector(repositoryRoot, markers)
                srcDir.walkTopDown().filter { it.isFile }.forEach { sourceFile ->
                    try {
                        findings.addAll(java.collectFrom(sourceFile))
                        findings.addAll(kotlin.collectFrom(sourceFile))
                    } catch (e: Exception) {
                        throw Exception("Unable to parse $sourceFile", e)
                    }
                }
            }
        }
        writeTextReport(findings.toList())
        writeHtmlReport(findings.toList(), targetMajor)
    }

    private
    fun writeTextReport(findings: List<RemovalFinding>) {
        val out = parameters.textReportFile.get().asFile
        out.parentFile.mkdirs()
        out.printWriter(Charsets.UTF_8).use { writer ->
            findings.forEach { writer.println(it.toRecord()) }
        }
    }

    private
    fun writeHtmlReport(findings: List<RemovalFinding>, targetMajor: Int) {
        val out = parameters.htmlReportFile.get().asFile
        val title = parameters.title.get()
        out.parentFile.mkdirs()
        out.printWriter(Charsets.UTF_8).use { writer ->
            writer.println(
                """<html lang="en">
    <head>
       <META http-equiv="Content-Type" content="text/html; charset=UTF-8">
       <title>Gradle $targetMajor removals for $title</title>
       <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Lato:400,400i,700">
       <meta content="width=device-width, initial-scale=1" name="viewport">
       <link type="text/css" rel="stylesheet" href="https://docs.gradle.org/current/userguide/base.css">
    </head>
    <body>
       <h1>Gradle $targetMajor removals for $title</h1>
       <p>${findings.size} deprecation call site(s) scheduled for removal / to become an error.</p>
"""
            )
            RemovalTimeline.Group.entries.forEach { group ->
                val inGroup = findings.filter { it.timeline.group == group }
                if (inGroup.isNotEmpty()) {
                    writer.println("<h2>${group.displayName} (${inGroup.size})</h2>")
                    writer.println("<ul>")
                    inGroup.forEach { f ->
                        val guide = if (f.guideSection != null && f.guideMajor != null) {
                            " — <a href=\"${upgradeGuideUrl(f.guideMajor, f.guideSection)}\">upgrade guide ${f.guideMajor}: ${f.guideSection}</a>"
                        } else {
                            " — no upgrade-guide section"
                        }
                        writer.println("   <li><code>${f.symbol.escape()}</code> [${f.kind.name.lowercase()}, ${f.timeline.method(targetMajor)}] at ${f.sourceRelativePath}:${f.lineNumber}$guide</li>")
                    }
                    writer.println("</ul>")
                }
            }
            writer.println("</body></html>")
        }
    }

    private
    fun String.escape() = replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        // Sort findings deterministically so the txt/html output is stable across runs (cache-friendly).
        // The trailing timeline/symbol keys also ensure the backing TreeSet only collapses true
        // duplicates, never two distinct findings that happen to share path+line (e.g. two markers on one line).
        private val FINDING_ORDER: Comparator<RemovalFinding> =
            compareBy(
                { it.timeline.group.ordinal },
                { it.guideSection ?: "~" },
                { it.sourceRelativePath.toString() },
                { it.lineNumber },
                { it.timeline.name },
                { it.symbol }
            )
    }
}


/** `timeline;kind;guideMajor;guideSection;relativePath;lineNumber;symbol` — symbol is last (may contain `;`). */
internal fun RemovalFinding.toRecord(): String = listOf(
    timeline.name,
    kind.name,
    guideMajor?.toString() ?: "",
    guideSection ?: "",
    sourceRelativePath.toString(),
    lineNumber.toString(),
    symbol.replace(NEWLINE_REGEX, " ")
).joinToString(";")


private val NEWLINE_REGEX = "\\s*\\n\\s*".toRegex()

/** Link to the upgrade guide section on the current docs (matches DocumentationRegistry's format). */
internal fun upgradeGuideUrl(major: Int, section: String): String =
    "https://docs.gradle.org/current/userguide/upgrading_version_$major.html#$section"

/**
 * The next major Gradle version — the target the removal report is about — derived from the current
 * version (e.g. `9.7.0` → 10, `10.2.0` → 11). Public so the convention plugins can compute it from
 * `version.txt`.
 */
fun nextMajorGradleVersion(currentVersion: String): Int =
    currentVersion.trim().substringBefore('.').toInt() + 1


private const val MAX_DYNAMIC_LENGTH = 120

internal fun dynamicSymbol(rawExpression: String?): String {
    val text = (rawExpression ?: "?").replace(NEWLINE_REGEX, " ")
    val capped = if (text.length > MAX_DYNAMIC_LENGTH) text.take(MAX_DYNAMIC_LENGTH) + "…" else text
    return "<dynamic: $capped>"
}


/**
 * Java chain analysis via JavaParser. No symbol solver: only simple names and literals are needed.
 */
class JavaRemovalCollector(
    private val repositoryRoot: Path,
    private val markersByMethod: Map<String, RemovalTimeline>
) {

    fun collectFrom(sourceFile: File): List<RemovalFinding> {
        if (!sourceFile.name.endsWith(".java")) return emptyList()
        val unit = JavaParser().parse(sourceFile).result.orElse(null) ?: return emptyList()
        return unit.findAll(MethodCallExpr::class.java)
            .mapNotNull { call ->
                val timeline = markersByMethod[call.nameAsString] ?: return@mapNotNull null
                analyze(call, timeline, sourceFile)
            }
    }

    private
    fun analyze(marker: MethodCallExpr, timeline: RemovalTimeline, file: File): RemovalFinding {
        val chain = chainCalls(marker)
        val root = chain.firstOrNull { it.nameAsString.startsWith("deprecate") }
        val kind = root?.let { RemovalKind.fromFactory(it.nameAsString) } ?: RemovalKind.OTHER
        val symbol = root?.let { symbolOf(it, kind) } ?: dynamicSymbol("no deprecate* call in chain")
        val guide = chain.firstOrNull { it.nameAsString == "withUpgradeGuideSection" }
        val major = (guide?.arguments?.getOrNull(0) as? IntegerLiteralExpr)?.value?.toIntOrNull()
        val section = (guide?.arguments?.getOrNull(1) as? StringLiteralExpr)?.value
        return RemovalFinding(
            timeline = timeline,
            kind = kind,
            symbol = symbol,
            guideMajor = major,
            guideSection = section,
            sourceRelativePath = repositoryRoot.relativize(file.toPath()),
            // Use the marker method-name token's line. A chained MethodCallExpr's own range starts at its
            // receiver (the deprecate* line), so marker.name pins the line where the timeline call appears,
            // consistent with the Kotlin collector.
            lineNumber = marker.name.begin.map { it.line }.orElse(-1)
        )
    }

    /** All `MethodCallExpr` in the fluent chain that [marker] belongs to, in source order. */
    private
    fun chainCalls(marker: MethodCallExpr): List<MethodCallExpr> {
        val calls = mutableListOf<MethodCallExpr>()
        // Walk down the receiver chain (the calls that precede the marker).
        var down: Expression? = marker
        while (down is MethodCallExpr) {
            calls.add(down)
            down = down.scope.orElse(null)
        }
        // Walk up while the parent is a chain call whose scope is the node we came from (the calls that follow).
        var node: Node = marker
        var parent = marker.parentNode.orElse(null)
        while (parent is MethodCallExpr && parent.scope.orElse(null) === node) {
            calls.add(parent)
            node = parent
            parent = parent.parentNode.orElse(null)
        }
        return calls
    }

    private
    fun symbolOf(root: MethodCallExpr, kind: RemovalKind): String {
        val args = root.arguments
        return when (kind) {
            RemovalKind.METHOD, RemovalKind.PROPERTY, RemovalKind.TASK_TYPE -> {
                val cls = (args.getOrNull(0) as? ClassExpr)?.type?.asString()
                val name = (args.getOrNull(1) as? StringLiteralExpr)?.value
                if (cls != null && name != null) "$cls.$name" else dynamicSymbol(args.firstOrNull()?.toString())
            }
            RemovalKind.TYPE -> {
                (args.getOrNull(0) as? ClassExpr)?.type?.asString() ?: dynamicSymbol(args.firstOrNull()?.toString())
            }
            else -> {
                (args.getOrNull(0) as? StringLiteralExpr)?.value ?: dynamicSymbol(args.firstOrNull()?.toString())
            }
        }
    }
}


/**
 * Kotlin chain analysis over PSI produced by [KotlinSourceParser].
 */
class KotlinRemovalCollector(
    private val repositoryRoot: Path,
    private val markersByMethod: Map<String, RemovalTimeline>
) {

    fun collectFrom(sourceFile: File): List<RemovalFinding> {
        if (!sourceFile.name.endsWith(".kt")) return emptyList()
        return KotlinSourceParser().mapParsedKotlinFiles(sourceFile) { ktFile ->
            collectFrom(ktFile, sourceFile)
        }.flatten()
    }

    private
    fun collectFrom(ktFile: KtFile, sourceFile: File): List<RemovalFinding> =
        ktFile.collectDescendantsOfType<KtCallExpression>()
            .mapNotNull { call ->
                val timeline = calleeName(call)?.let { markersByMethod[it] } ?: return@mapNotNull null
                analyze(call, timeline, ktFile, sourceFile)
            }

    private
    fun analyze(marker: KtCallExpression, timeline: RemovalTimeline, ktFile: KtFile, sourceFile: File): RemovalFinding {
        val chain = chainCalls(marker)
        val root = chain.firstOrNull { calleeName(it)?.startsWith("deprecate") == true }
        val kind = root?.let { RemovalKind.fromFactory(calleeName(it)!!) } ?: RemovalKind.OTHER
        val symbol = root?.let { symbolOf(it, kind) } ?: dynamicSymbol("no deprecate* call in chain")
        val guide = chain.firstOrNull { calleeName(it) == "withUpgradeGuideSection" }
        val guideArgs = guide?.valueArguments?.mapNotNull { it.getArgumentExpression() }
        val major = guideArgs?.getOrNull(0)?.text?.toIntOrNull()
        val section = guideArgs?.getOrNull(1)?.let { stringValue(it) }
        return RemovalFinding(
            timeline = timeline,
            kind = kind,
            symbol = symbol,
            guideMajor = major,
            guideSection = section,
            sourceRelativePath = repositoryRoot.relativize(sourceFile.toPath()),
            lineNumber = lineNumber(marker, ktFile)
        )
    }

    private
    fun calleeName(call: KtCallExpression): String? =
        (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()

    /** All chain `KtCallExpression`s along the receiver spine that [marker] belongs to, in source order. */
    private
    fun chainCalls(marker: KtCallExpression): List<KtCallExpression> {
        // Find the top of the dot-qualified chain that has marker as a selector somewhere on its spine.
        var top: KtExpression = (marker.parent as? KtDotQualifiedExpression) ?: return listOf(marker)
        while (true) {
            val parent = top.parent
            if (parent is KtDotQualifiedExpression && parent.receiverExpression === top) {
                top = parent
            } else {
                break
            }
        }
        // Walk the spine from the top down through receivers, collecting each selector call.
        val calls = ArrayDeque<KtCallExpression>()
        var e: KtExpression? = top
        while (e is KtDotQualifiedExpression) {
            (e.selectorExpression as? KtCallExpression)?.let { calls.addFirst(it) }
            e = e.receiverExpression
        }
        (e as? KtCallExpression)?.let { calls.addFirst(it) }
        return calls.toList()
    }

    private
    fun symbolOf(root: KtCallExpression, kind: RemovalKind): String {
        val args = root.valueArguments.mapNotNull { it.getArgumentExpression() }
        return when (kind) {
            RemovalKind.METHOD, RemovalKind.PROPERTY, RemovalKind.TASK_TYPE -> {
                val cls = classLiteralSimpleName(args.getOrNull(0))
                val name = args.getOrNull(1)?.let { stringValue(it) }
                if (cls != null && name != null) "$cls.$name" else dynamicSymbol(args.firstOrNull()?.text)
            }
            RemovalKind.TYPE -> classLiteralSimpleName(args.getOrNull(0)) ?: dynamicSymbol(args.firstOrNull()?.text)
            else -> args.getOrNull(0)?.let { stringValue(it) } ?: dynamicSymbol(args.firstOrNull()?.text)
        }
    }

    /** Resolves `Foo::class.java` / `Foo::class` to `Foo`, else null. */
    private
    fun classLiteralSimpleName(expr: KtExpression?): String? {
        val classLiteral = when (expr) {
            is KtClassLiteralExpression -> expr
            is KtDotQualifiedExpression -> expr.receiverExpression as? KtClassLiteralExpression
            else -> null
        } ?: return null
        return classLiteral.receiverExpression?.text?.substringAfterLast('.')
    }

    /** Returns the constant value of a string-template expression, or null if it interpolates. */
    private
    fun stringValue(expr: KtExpression): String? {
        if (expr !is KtStringTemplateExpression) return null
        if (expr.entries.any { it is KtStringTemplateEntryWithExpression }) return null
        return expr.entries.joinToString("") { it.text }
    }

    private
    fun lineNumber(call: KtCallExpression, ktFile: KtFile): Int =
        ktFile.viewProvider.document?.getLineNumber(call.textRange.startOffset)?.plus(1) ?: -1
}
