/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.dom.mutation

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer


typealias ElementResolution = Pair<DocumentNode.ElementNode, DocumentResolution.ElementResolution>


data class Scope(val elements: List<ScopeElement>) {

    companion object Factory {

        fun topLevel(): Scope = Scope(emptyList())

        fun nestedBlocks(elementNodes: List<ElementResolution>): Scope =
            Scope(elementNodes.map { ScopeElement(it) })

        fun nestedBlocks(vararg elementNodes: ElementResolution): Scope =
            nestedBlocks(elementNodes.toList())
    }
}


data class ScopeElement(val elementNodes: ElementResolution)


data class ScopeLocation(val elements: List<ScopeLocationElement>) {

    private
    val nfa: NFA by lazy {
        NFA(this)
    }

    fun match(document: DeclarativeDocument, resolution: DocumentResolutionContainer): Set<Scope> =
        findAllPossibleScopes(document, resolution).filter { nfa.matches(it) }.toSet()

    private
    fun findAllPossibleScopes(document: DeclarativeDocument, resolution: DocumentResolutionContainer): Set<Scope> {
        val scopes = mutableSetOf(Scope.topLevel())
        val path = mutableListOf<DocumentNode.ElementNode>()

        fun toScope(elementNodes: List<DocumentNode.ElementNode>, resolution: DocumentResolutionContainer): Scope {
            return Scope.nestedBlocks(elementNodes.map { it to resolution.data(it) })
        }

        fun visitNode(node: DocumentNode) {
            when (node) {
                is DocumentNode.ElementNode -> {
                    path.add(node)

                    var isNotLeaf = false
                    node.content.forEach {
                        isNotLeaf = isNotLeaf || it is DocumentNode.ElementNode
                        visitNode(it)
                    }

                    scopes.add(toScope(path, resolution))

                    path.removeLast()
                }

                is DocumentNode.PropertyNode -> Unit

                is DocumentNode.ErrorNode -> TODO()
            }
        }

        document.content.forEach(::visitNode)

        return scopes
    }
}


sealed interface ScopeLocationElement {

    fun matches(scopeElement: ScopeElement): Boolean

    data object InAllNestedScopes : ScopeLocationElement {
        override fun matches(scopeElement: ScopeElement) = true
    }

    data class InNestedScopes(val nestedScopeSelector: NestedScopeSelector) : ScopeLocationElement {
        override fun matches(scopeElement: ScopeElement): Boolean =
            nestedScopeSelector.matches(scopeElement)
    }
}


sealed interface NestedScopeSelector {

    fun matches(scopeElement: ScopeElement): Boolean

    data class NestedObjectsOfType(val type: DataClass) : NestedScopeSelector {
        override fun matches(scopeElement: ScopeElement): Boolean =
            when (val elementResolution = scopeElement.elementNodes.second) {
                is DocumentResolution.ElementResolution.SuccessfulElementResolution -> elementResolution.elementType == type
                is DocumentResolution.ElementResolution.ElementNotResolved -> TODO()
            }
    }

    data class ObjectsConfiguredBy(val function: DataMemberFunction, val argumentsPattern: ArgumentsPattern = ArgumentsPattern.AnyArguments) : NestedScopeSelector {
        override fun matches(scopeElement: ScopeElement): Boolean =
            when (val elementResolution = scopeElement.elementNodes.second) {
                is DocumentResolution.ElementResolution.SuccessfulElementResolution -> when (elementResolution) {
                    is DocumentResolution.ElementResolution.SuccessfulElementResolution.ConfiguringElementResolved -> TODO()
                    is DocumentResolution.ElementResolution.SuccessfulElementResolution.ContainerElementResolved -> elementResolution.elementFactoryFunction == function
                }

                is DocumentResolution.ElementResolution.ElementNotResolved -> TODO()
            }
    }
}


sealed interface IfNotFoundBehavior {
    data object Ignore : IfNotFoundBehavior
    data object FailAndReport : IfNotFoundBehavior
}


sealed interface ArgumentsPattern {
    data object AnyArguments : ArgumentsPattern

    data class MatchesArguments(
        /**
         * These are arguments that a call site should have. They are matched as [DeclarativeDocument.ValueNode]s structurally, and their
         * resolutions are considered equal if they are resolved to the same declaration or are both unresolved (for any reason). Actual arguments that are present at
         * a call site but not in this map are considered as matching arguments.
         */
        val shouldHaveArguments: Map<DataParameter, ValueMatcher>
    ) : ArgumentsPattern
}


sealed interface ValueMatcher {
    data class MatchLiteral(val literalValue: Any) : ValueMatcher
    data class MatchValueFactory(val valueFactory: SchemaMemberFunction, val args: ArgumentsPattern) : ValueMatcher
}


private
class NFA(
    private val scopeLocation: ScopeLocation
) {
    private
    val matchTransitions: Digraph

    private
    val epsilonTransitions: Digraph

    private
    val noOfStates = scopeLocation.elements.size + 1 // number of elements in scope + finish state

    init {
        matchTransitions = Digraph(noOfStates)
        epsilonTransitions = Digraph(noOfStates)
        for (i in 0 until scopeLocation.elements.size) {
            val scopeLocationElement = scopeLocation.elements[i]

            when (scopeLocationElement) {
                is ScopeLocationElement.InAllNestedScopes -> {
                    matchTransitions.addEdge(i, i)
                    epsilonTransitions.addEdge(i, i + 1)
                }

                is ScopeLocationElement.InNestedScopes -> {
                    matchTransitions.addEdge(i, i + 1)
                }
            }
        }
    }

    fun matches(scope: Scope): Boolean {
        var possibleStates = MarkedStates(noOfStates)
        possibleStates.mark(epsilonTransitions, 0)

        var extraStates = MarkedStates(noOfStates)


        for (scopeElement in scope.elements) {
            extraStates.clear()

            for (i in 0 until scopeLocation.elements.size) {
                if (possibleStates[i]) { // each possible state
                    val scopeLocationElement = scopeLocation.elements[i]
                    if (scopeLocationElement.matches(scopeElement)) {
                        matchTransitions.adjacentVertices(i).forEach {
                            extraStates.mark(epsilonTransitions, it)
                        }
                    }
                }
            }

            val temp = possibleStates
            possibleStates = extraStates
            extraStates = temp
        }

        val matchStatePossible = possibleStates.last()
        return matchStatePossible
    }

    override fun toString(): String {
        return buildString {
            appendLine("NFA with $noOfStates vertices, having edges:")
            for (i in 0 until noOfStates) {
                appendLine("\t$i -> ${epsilonTransitions.adjacentVertices(i)}")
            }
        }
    }
}


private
class MarkedStates(val size: Int) {

    private
    val marked = BooleanArray(size) { false }

    fun clear() {
        for (i in 0 until size) {
            marked[i] = false
        }
    }

    fun mark(graph: Digraph, vertex: Int) {
        marked[vertex] = true
        for (w in graph.adjacentVertices(vertex)) {
            if (!marked[w]) mark(graph, w)
        }
    }

    fun last(): Boolean = marked.last()

    operator fun get(vertex: Int) =
        marked[vertex]

    override fun toString(): String {
        return buildString {
            appendLine("Reachable states out of $size:")
            for (i in 0 until size) {
                appendLine("\t$i -> ${marked[i]}")
            }
        }
    }
}


private
class Digraph(vertices: Int) {

    private
    val adj: List<MutableList<Int>> // adj[v] = adjacency list for vertex v

    init {
        require(vertices >= 0) { "Number of vertices in a Digraph must be non-negative" }
        adj = buildList {
            for (v in 0 until vertices) {
                add(mutableListOf())
            }
        }
    }


    fun addEdge(v: Int, w: Int) {
        adj[v].add(w)
    }

    fun adjacentVertices(vertex: Int): Iterable<Int> {
        return adj[vertex]
    }
}
