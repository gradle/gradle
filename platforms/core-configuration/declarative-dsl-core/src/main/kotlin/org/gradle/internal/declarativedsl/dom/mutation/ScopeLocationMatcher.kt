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
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.ElementNotResolved
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.SuccessfulElementResolution
import org.gradle.internal.declarativedsl.dom.mutation.NestedScopeSelector.NestedObjectsOfType
import org.gradle.internal.declarativedsl.dom.mutation.NestedScopeSelector.ObjectsConfiguredBy
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution


internal
class ScopeLocationMatcher(
    private val topLevelType: DataClass,
    private val documentWithResolution: DocumentWithResolution,
    private val documentMemberMatcher: DocumentMemberAndTypeMatcher
) {
    private
    val resolution: DocumentResolutionContainer
        get() = documentWithResolution.resolutionContainer

    private
    val allScopes: Set<Scope> = findAllPossibleScopes()

    fun match(scopeLocation: ScopeLocation): Set<Scope> {
        val locationNfa = NFA(scopeLocation)
        return allScopes.filter { locationNfa.matches(it, ::isScopeElementMatchedByLocationElement) }.toSet()
    }

    private
    fun findAllPossibleScopes(): Set<Scope> {
        val scopes = mutableSetOf(Scope.topLevel())
        val path = mutableListOf<ElementNode>()

        fun visitNode(node: DocumentNode) {
            when (node) {
                is ElementNode -> {
                    path.add(node)

                    var isNotLeaf = false
                    node.content.forEach {
                        isNotLeaf = isNotLeaf || it is ElementNode
                        visitNode(it)
                    }

                    scopes.add(Scope(path.toList()))

                    path.removeLast()
                }

                is DocumentNode.PropertyNode -> Unit

                is DocumentNode.ErrorNode -> Unit
            }
        }

        documentWithResolution.document.content.forEach(::visitNode)

        return scopes
    }

    private
    fun isScopeElementMatchedByLocationElement(
        locationElement: ScopeLocationElement,
        parentScopeElement: ElementNode?,
        scopeElement: ElementNode
    ): Boolean {
        return when (locationElement) {
            ScopeLocationElement.InAllNestedScopes -> true
            is ScopeLocationElement.InNestedScopes -> {
                val parentScopeType = if (parentScopeElement == null)
                    topLevelType
                else {
                    val parentResolution = resolution.data(parentScopeElement)
                    if (parentResolution !is SuccessfulElementResolution) {
                        return false
                    }
                    parentResolution.elementType as? DataClass ?: return false
                }
                isScopeElementMatchedBySelector(locationElement.nestedScopeSelector, parentScopeType, scopeElement)
            }
        }
    }

    private
    fun isScopeElementMatchedBySelector(
        selector: NestedScopeSelector,
        scopeElementOwner: DataClass,
        scopeElement: ElementNode
    ): Boolean = when (val resolvedTo = resolution.data(scopeElement)) {
        is ElementNotResolved -> false
        is SuccessfulElementResolution -> when (selector) {
            is NestedObjectsOfType -> documentMemberMatcher.typeIsSubtypeOf(resolvedTo.elementType, selector.type)
            is ObjectsConfiguredBy -> documentMemberMatcher.isSameFunctionOrOverrides(TypedMember.TypedFunction(scopeElementOwner, resolvedTo.elementFactoryFunction), selector.function)
        }
    }
}


internal
data class Scope(val elements: List<ElementNode>) {
    companion object Factory {
        fun topLevel(): Scope = Scope(emptyList())
    }
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

    @Suppress("NestedBlockDepth")
    fun matches(scope: Scope, matchTransition: (ScopeLocationElement, parentElement: ElementNode?, scopeElement: ElementNode) -> Boolean): Boolean {
        var possibleStates = MarkedStates(noOfStates)
        possibleStates.mark(epsilonTransitions, 0)

        var extraStates = MarkedStates(noOfStates)
        var parentScopeElement: ElementNode? = null

        for (scopeElement in scope.elements) {
            extraStates.clear()

            for (i in 0 until scopeLocation.elements.size) {
                if (possibleStates[i]) { // each possible state
                    val scopeLocationElement = scopeLocation.elements[i]
                    if (matchTransition(scopeLocationElement, parentScopeElement, scopeElement)) {
                        matchTransitions.adjacentVertices(i).forEach {
                            extraStates.mark(epsilonTransitions, it)
                        }
                    }
                }
            }

            val temp = possibleStates
            possibleStates = extraStates
            extraStates = temp

            parentScopeElement = scopeElement
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
