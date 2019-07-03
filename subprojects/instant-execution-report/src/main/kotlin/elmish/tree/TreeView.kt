/*
 * Copyright 2019 the original author or authors.
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

package elmish.tree

import data.mapAt
import elmish.View
import elmish.attributes
import elmish.div
import elmish.empty
import elmish.li
import elmish.ul


object TreeView {

    data class Model<T>(
        val tree: Tree<T>
    )

    sealed class Intent<T> {
        data class Toggle<T>(val focus: Tree.Focus<T>) : Intent<T>()
    }

    fun <T> view(model: Model<T>): View<Intent<T>> =
        viewTree<T, Intent<T>>(model.tree.focus()) { focus ->
            div(
                attributes {
                    onClick { Intent.Toggle(focus) }
                },
                focus.tree.label.toString()
            )
        }

    fun <T> step(intent: Intent<T>, model: Model<T>): Model<T> = when (intent) {
        is Intent.Toggle -> model.copy(
            tree = intent.focus.update {
                copy(state = state.toggle())
            }
        )
    }
}


/**
 * A persistent tree view model.
 */
data class Tree<T>(
    val label: T,
    val children: List<Tree<T>> = emptyList(),
    val state: ViewState = ViewState.Collapsed
) {

    enum class ViewState {

        Collapsed,
        Expanded;

        fun toggle(): ViewState = when (this) {
            Collapsed -> Expanded
            Expanded -> Collapsed
        }
    }

    /**
     * Creates an updatable reference to this tree.
     */
    fun focus(): Focus<T> = Focus.Original(
        this
    )

    fun isNotEmpty(): Boolean =
        children.isNotEmpty()

    /**
     * Propagates changes to a particular node in the tree all the way
     * up to the root (the [focused][focus] tree).
     */
    sealed class Focus<T> {

        abstract val tree: Tree<T>

        abstract fun update(f: Tree<T>.() -> Tree<T>): Tree<T>

        val children
            get() = tree.children.indices.asSequence().map(::child)

        fun child(index: Int): Focus<T> = Child(
            this,
            index,
            tree.children[index]
        )

        data class Original<T>(
            override val tree: Tree<T>
        ) : Focus<T>() {

            override fun update(f: Tree<T>.() -> Tree<T>): Tree<T> = f(tree)
        }

        data class Child<T>(
            val parent: Focus<T>,
            val index: Int,
            override val tree: Tree<T>
        ) : Focus<T>() {

            override fun update(f: Tree<T>.() -> Tree<T>): Tree<T> = parent.update {
                copy(children = children.mapAt(index, f))
            }
        }
    }
}


fun <T, I> viewTree(
    focus: Tree.Focus<T>,
    viewLabel: (Tree.Focus<T>) -> View<I>
): View<I> = ul(
    viewSubTree(focus, viewLabel)
)


fun <T, I> viewSubTree(
    focus: Tree.Focus<T>,
    viewLabel: (Tree.Focus<T>) -> View<I>
): View<I> = focus.tree.run {
    li(
        viewLabel(focus),
        children.takeIf { state == Tree.ViewState.Expanded && it.isNotEmpty() }?.run {
            viewExpanded(focus, viewLabel)
        } ?: empty
    )
}


fun <T, I> viewExpanded(focus: Tree.Focus<T>, viewLabel: Tree.Focus<T>.() -> View<I>): View<I> =
    ul(viewChildrenOf(focus, viewLabel))


fun <I, T> viewChildrenOf(
    focus: Tree.Focus<T>,
    viewLabel: (Tree.Focus<T>) -> View<I>
): List<View<I>> = viewSubTrees(focus.children, viewLabel)


fun <I, T> viewSubTrees(
    subTrees: Sequence<Tree.Focus<T>>,
    viewLabel: (Tree.Focus<T>) -> View<I>
): List<View<I>> = subTrees
    .map { subTree -> viewSubTree(subTree, viewLabel) }
    .toList()
