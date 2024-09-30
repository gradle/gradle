package org.gradle.client.core.gradle.dcl

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.UnresolvedBase
import org.gradle.internal.declarativedsl.dom.data.collectToMap
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlay.overlayResolvedDocuments
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils.resolvedDocument
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisSequenceResult
import org.gradle.internal.declarativedsl.evaluator.runner.stepResultOrPartialResult
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.SyntheticallyProduced
import java.util.*

fun DeclarativeDocument.relevantRange(): IntRange {
    val first = content.firstOrNull() ?: return IntRange.EMPTY
    val last = content.last()
    return IntRange(first.sourceData.indexRange.first, last.sourceData.indexRange.last)
}

fun DocumentWithResolution.errorRanges(): List<IntRange> =
    resolutionContainer.collectToMap(document).entries
        .filter { it.value is DocumentResolution.UnsuccessfulResolution }
        .map { it.key.sourceData.indexRange }

fun DeclarativeDocument.nodeAt(fileIdentifier: String, offset: Int): DeclarativeDocument.DocumentNode? {
    var node: DeclarativeDocument.DocumentNode? = null
    val stack: Deque<DeclarativeDocument.DocumentNode> = LinkedList()

    fun List<DeclarativeDocument.DocumentNode>.matchingContent(): List<DeclarativeDocument.DocumentNode> =
        filter {
            it.sourceData.sourceIdentifier.fileIdentifier == fileIdentifier &&
                    it.sourceData.indexRange.contains(offset)
        }

    stack.addAll(content.matchingContent())
    while (stack.isNotEmpty()) {
        when (val current = stack.pop()) {
            is DeclarativeDocument.DocumentNode.ElementNode -> {
                node = current
                stack.addAll(current.content.matchingContent())
            }

            else -> {
                node = current
            }
        }
    }
    return node
}

fun settingsWithNoOverlayOrigin(analysisSequenceResult: AnalysisSequenceResult): DocumentOverlayResult? {
    val docs = analysisSequenceResult.stepResults.map { it.value.stepResultOrPartialResult.resolvedDocument() }
    if (docs.isEmpty())
        return null

    return indexBasedOverlayResultFromDocuments(docs)
}


internal fun indexBasedOverlayResultFromDocuments(docs: List<DocumentWithResolution>): DocumentOverlayResult {
    val emptyDoc = DocumentWithResolution(
        object : DeclarativeDocument {
            override val content: List<DeclarativeDocument.DocumentNode> = emptyList()
            override val sourceData: SourceData = SyntheticallyProduced
        },
        indexBasedMultiResolutionContainer(emptyList())
    )

    val lastDocWithAllResolutionResults = DocumentWithResolution(
        docs.last().document,
        indexBasedMultiResolutionContainer(docs)
    )

    /**
     * NB: No real overlay origin data is going to be present, as we are overlaying the doc with all the resolution
     * results collected over the empty document.
     */
    return overlayResolvedDocuments(emptyDoc, lastDocWithAllResolutionResults)
}

/**
 * A resolution results container collected from multiple resolved instances of the same document (or multiple
 * different instances of the same document, no referential equality required).
 * 
 * The document parts are matched based on indices.
 * 
 * If any of the [docs] is different from the others, the result is undefined (likely to be a broken container).
 */
internal fun indexBasedMultiResolutionContainer(docs: List<DocumentWithResolution>): DocumentResolutionContainer {
    val indicesMaps: Map<DocumentWithResolution, Map<IntRange, DeclarativeDocument.Node>> = docs.associateWith {
        buildMap {
            fun visitValue(valueNode: DeclarativeDocument.ValueNode) {
                put(valueNode.sourceData.indexRange, valueNode)
                when (valueNode) {
                    is DeclarativeDocument.ValueNode.ValueFactoryNode -> valueNode.values.forEach(::visitValue)
                    is DeclarativeDocument.ValueNode.LiteralValueNode,
                    is DeclarativeDocument.ValueNode.NamedReferenceNode -> Unit
                }
            }

            fun visitDocumentNode(documentNode: DeclarativeDocument.DocumentNode) {
                put(documentNode.sourceData.indexRange, documentNode)
                when (documentNode) {
                    is DeclarativeDocument.DocumentNode.ElementNode -> {
                        documentNode.elementValues.forEach(::visitValue)
                        documentNode.content.forEach(::visitDocumentNode)
                    }

                    is DeclarativeDocument.DocumentNode.PropertyNode -> visitValue(documentNode.value)
                    is DeclarativeDocument.DocumentNode.ErrorNode -> Unit
                }
            }

            it.document.content.forEach(::visitDocumentNode)
        }
    }

    /**
     * The resolution containers work with node identities.
     * Querying resolution results using nodes from a different document is prohibited.
     * Given that all documents are the same, we can map the node indices and use them to find matching nodes in
     * the documents that we are merging.
     */
    return object : DocumentResolutionContainer {
        inline fun <reified N : DeclarativeDocument.Node, reified T> retryOverContainers(
            node: N,
            noinline get: DocumentResolutionContainer.(N) -> T
        ) = docs.map { doc ->
            val matchingNode = indicesMaps.getValue(doc)[node.sourceData.indexRange]
                ?: error("index not found in index map")
            get(doc.resolutionContainer, matchingNode as N)
        }.let { results ->
            results.firstOrNull {
                it !is DocumentResolution.UnsuccessfulResolution || !it.reasons.contains(UnresolvedBase)
            } ?: results.first()
        }

        override fun data(node: DeclarativeDocument.DocumentNode.ElementNode) = retryOverContainers(node) { data(it) }
        override fun data(node: DeclarativeDocument.DocumentNode.ErrorNode) = retryOverContainers(node) { data(it) }
        override fun data(node: DeclarativeDocument.DocumentNode.PropertyNode) = retryOverContainers(node) { data(it) }
        override fun data(node: DeclarativeDocument.ValueNode.LiteralValueNode) = retryOverContainers(node) { data(it) }
        override fun data(node: DeclarativeDocument.ValueNode.NamedReferenceNode) =
            retryOverContainers(node) { data(it) }

        override fun data(node: DeclarativeDocument.ValueNode.ValueFactoryNode) = retryOverContainers(node) { data(it) }
    }
}

internal fun DocumentResolutionContainer.isUnresolvedBase(node: DeclarativeDocument.Node): Boolean {
    val resolution = when (node) {
        is DeclarativeDocument.DocumentNode -> data(node)
        is DeclarativeDocument.ValueNode -> data(node)
    }
    return resolution is DocumentResolution.UnsuccessfulResolution && resolution.reasons != listOf(UnresolvedBase)
}
