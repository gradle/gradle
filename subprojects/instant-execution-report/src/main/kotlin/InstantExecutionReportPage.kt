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

import elmish.Component
import elmish.View
import elmish.a
import elmish.attributes
import elmish.code
import elmish.div
import elmish.empty
import elmish.h1
import elmish.h2
import elmish.ol
import elmish.pre
import elmish.small
import elmish.span
import elmish.tree.Tree
import elmish.tree.TreeView
import elmish.tree.viewSubTrees

import kotlin.browser.window


internal
sealed class ProblemNode {

    data class Error(val label: ProblemNode) : ProblemNode()

    data class Warning(val label: ProblemNode) : ProblemNode()

    data class Task(val path: String, val type: String) : ProblemNode()

    data class Bean(val type: String) : ProblemNode()

    data class Property(val kind: String, val name: String, val owner: String) : ProblemNode()

    data class Label(val text: String) : ProblemNode()

    data class Message(val prettyText: PrettyText) : ProblemNode()

    data class Exception(val stackTrace: String) : ProblemNode()
}


internal
data class PrettyText(val fragments: List<Fragment>) {

    sealed class Fragment {

        data class Text(val text: String) : Fragment()

        data class Reference(val name: String) : Fragment()
    }
}


internal
typealias ProblemTreeModel = TreeView.Model<ProblemNode>


internal
typealias ProblemTreeIntent = TreeView.Intent<ProblemNode>


internal
object InstantExecutionReportPage : Component<InstantExecutionReportPage.Model, InstantExecutionReportPage.Intent> {

    data class Model(
        val totalProblems: Int,
        val messageTree: ProblemTreeModel,
        val taskTree: ProblemTreeModel,
        val displayFilter: DisplayFilter = DisplayFilter.All
    )

    enum class DisplayFilter {
        All, Errors, Warnings
    }

    sealed class Intent {

        data class TaskTreeIntent(val delegate: ProblemTreeIntent) : Intent()

        data class MessageTreeIntent(val delegate: ProblemTreeIntent) : Intent()

        data class Copy(val text: String) : Intent()

        data class SetFilter(val displayFilter: DisplayFilter) : Intent()
    }

    override fun step(intent: Intent, model: Model): Model = when (intent) {
        is Intent.TaskTreeIntent -> model.copy(
            taskTree = TreeView.step(intent.delegate, model.taskTree)
        )
        is Intent.MessageTreeIntent -> model.copy(
            messageTree = TreeView.step(intent.delegate, model.messageTree)
        )
        is Intent.Copy -> {
            window.navigator.clipboard.writeText(intent.text)
            model
        }
        is Intent.SetFilter -> model.copy(
            displayFilter = intent.displayFilter
        )
    }

    override fun view(model: Model): View<Intent> = div(
        attributes { className("container") },
        div(
            div(
                attributes { className("right") },
                div(
                    displayFilterButton(DisplayFilter.All, model.displayFilter),
                    displayFilterButton(DisplayFilter.Errors, model.displayFilter),
                    displayFilterButton(DisplayFilter.Warnings, model.displayFilter)
                )
            ),
            div(
                attributes { className("left") },
                h1("${model.totalProblems} instant execution problems were found"),
                learnMore(),
                viewTree(model.messageTree, Intent::MessageTreeIntent, model.displayFilter),
                viewTree(model.taskTree, Intent::TaskTreeIntent, model.displayFilter)
            )
        )
    )

    private
    fun displayFilterButton(displayFilter: DisplayFilter, activeFilter: DisplayFilter): View<Intent> = span(
        attributes {
            className("btn")
            if (displayFilter == activeFilter) {
                className("btn-active")
            }
            onClick { Intent.SetFilter(displayFilter) }
        },
        displayFilter.name
    )

    private
    fun learnMore(): View<Intent> = div(
        span("Learn more about "),
        a(
            attributes { href("https://gradle.github.io/instant-execution/") },
            "Gradle Instant Execution"
        ),
        span(".")
    )

    private
    fun viewTree(model: ProblemTreeModel, treeIntent: (ProblemTreeIntent) -> Intent, displayFilter: DisplayFilter): View<Intent> = div(
        h2(model.tree.label.unsafeCast<ProblemNode.Label>().text),
        ol(
            viewSubTrees(applyFilter(displayFilter, model)) { child ->
                when (val node = child.tree.label) {
                    is ProblemNode.Error -> {
                        viewLabel(treeIntent, child, node.label, errorIcon)
                    }
                    is ProblemNode.Warning -> {
                        viewLabel(treeIntent, child, node.label, warningIcon)
                    }
                    is ProblemNode.Exception -> {
                        viewException(treeIntent, child, node)
                    }
                    else -> {
                        viewLabel(treeIntent, child, node)
                    }
                }
            }
        )
    )

    private
    fun applyFilter(displayFilter: DisplayFilter, model: ProblemTreeModel): Sequence<Tree.Focus<ProblemNode>> {
        val children = model.tree.focus().children
        return when (displayFilter) {
            DisplayFilter.All -> children
            DisplayFilter.Errors -> children.filter { it.tree.label is ProblemNode.Error }
            DisplayFilter.Warnings -> children.filter { it.tree.label is ProblemNode.Warning }
        }
    }

    private
    fun viewNode(node: ProblemNode): View<Intent> = when (node) {
        is ProblemNode.Property -> span(
            span(node.kind),
            reference(node.name),
            span(" of "),
            reference(node.owner)
        )
        is ProblemNode.Task -> span(
            span("task"),
            reference(node.path),
            span(" of type "),
            reference(node.type)
        )
        is ProblemNode.Bean -> span(
            span("bean of type "),
            reference(node.type)
        )
        is ProblemNode.Label -> span(
            node.text
        )
        is ProblemNode.Message -> viewPrettyText(
            node.prettyText
        )
        else -> span(
            node.toString()
        )
    }

    private
    fun viewLabel(
        treeIntent: (ProblemTreeIntent) -> Intent,
        child: Tree.Focus<ProblemNode>,
        label: ProblemNode,
        decoration: View<Intent> = empty
    ): View<Intent> = div(
        treeButtonFor(child, treeIntent),
        decoration,
        span(" "),
        viewNode(label)
    )

    private
    fun treeButtonFor(child: Tree.Focus<ProblemNode>, treeIntent: (ProblemTreeIntent) -> Intent): View<Intent> =
        when {
            child.tree.isNotEmpty() -> viewTreeButton(child, treeIntent)
            else -> emptyTreeIcon
        }

    private
    fun viewTreeButton(child: Tree.Focus<ProblemNode>, treeIntent: (ProblemTreeIntent) -> Intent): View<Intent> = span(
        attributes {
            className("tree-btn")
            title("Click to ${toggleVerb(child.tree.state)}")
            onClick { treeIntent(TreeView.Intent.Toggle(child)) }
        },
        when (child.tree.state) {
            Tree.ViewState.Collapsed -> "► "
            Tree.ViewState.Expanded -> "▼ "
        }
    )

    private
    val errorIcon = span<Intent>(" ❌")

    private
    val warningIcon = span<Intent>(" ⚠️")

    private
    val emptyTreeIcon = span<Intent>(
        attributes { className("tree-icon") },
        "■ "
    )

    private
    fun viewPrettyText(text: PrettyText): View<Intent> = span(
        text.fragments.map {
            when (it) {
                is PrettyText.Fragment.Text -> span(it.text)
                is PrettyText.Fragment.Reference -> reference(it.name)
            }
        }
    )

    private
    fun reference(name: String): View<Intent> = span(
        code(name),
        copyButton(
            text = name,
            tooltip = "Copy reference to the clipboard"
        )
    )

    private
    fun copyButton(text: String, tooltip: String): View<Intent> = small(
        attributes {
            title(tooltip)
            className("copy-button")
            onClick { Intent.Copy(text) }
        },
        "📋"
    )

    private
    fun viewException(
        treeIntent: (ProblemTreeIntent) -> Intent,
        child: Tree.Focus<ProblemNode>,
        node: ProblemNode.Exception
    ): View<Intent> = div(
        viewTreeButton(child, treeIntent),
        span("exception stack trace "),
        copyButton(
            text = node.stackTrace,
            tooltip = "Copy original stacktrace to the clipboard"
        ),
        when (child.tree.state) {
            Tree.ViewState.Collapsed -> empty
            Tree.ViewState.Expanded -> pre(
                attributes { className("stacktrace") },
                node.stackTrace
            )
        }
    )

    private
    fun toggleVerb(state: Tree.ViewState): String = when (state) {
        Tree.ViewState.Collapsed -> "expand"
        Tree.ViewState.Expanded -> "collapse"
    }
}
