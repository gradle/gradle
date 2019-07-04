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
sealed class FailureNode {

    data class Error(val label: FailureNode) : FailureNode()

    data class Warning(val label: FailureNode) : FailureNode()

    data class Task(val path: String, val type: String) : FailureNode()

    data class Bean(val type: String) : FailureNode()

    data class Property(val kind: String, val name: String, val owner: String) : FailureNode()

    data class Label(val text: String) : FailureNode()

    data class Message(val prettyText: PrettyText) : FailureNode()

    data class Exception(val stackTrace: String) : FailureNode()
}


internal
data class PrettyText(val fragments: List<Fragment>) {

    sealed class Fragment {

        data class Text(val text: String) : Fragment()

        data class Reference(val name: String) : Fragment()
    }
}


internal
typealias FailureTreeModel = TreeView.Model<FailureNode>


internal
typealias FailureTreeIntent = TreeView.Intent<FailureNode>


internal
object InstantExecutionReportPage : Component<InstantExecutionReportPage.Model, InstantExecutionReportPage.Intent> {

    data class Model(
        val totalFailures: Int,
        val messageTree: FailureTreeModel,
        val taskTree: FailureTreeModel,
        val displayFilter: DisplayFilter = DisplayFilter.All
    )

    enum class DisplayFilter {
        All, Errors, Warnings
    }

    sealed class Intent {

        data class TaskTreeIntent(val delegate: FailureTreeIntent) : Intent()

        data class MessageTreeIntent(val delegate: FailureTreeIntent) : Intent()

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
                h1("${model.totalFailures} instant execution problems were found"),
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
    fun viewTree(model: FailureTreeModel, treeIntent: (FailureTreeIntent) -> Intent, displayFilter: DisplayFilter): View<Intent> = div(
        h2(model.tree.label.unsafeCast<FailureNode.Label>().text),
        ol(
            viewSubTrees(applyFilter(displayFilter, model)) { child ->
                when (val node = child.tree.label) {
                    is FailureNode.Error -> {
                        viewLabel(treeIntent, child, node.label, errorIcon)
                    }
                    is FailureNode.Warning -> {
                        viewLabel(treeIntent, child, node.label, warningIcon)
                    }
                    is FailureNode.Exception -> {
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
    fun applyFilter(displayFilter: DisplayFilter, model: FailureTreeModel): Sequence<Tree.Focus<FailureNode>> {
        val children = model.tree.focus().children
        return when (displayFilter) {
            DisplayFilter.All -> children
            DisplayFilter.Errors -> children.filter { it.tree.label is FailureNode.Error }
            DisplayFilter.Warnings -> children.filter { it.tree.label is FailureNode.Warning }
        }
    }

    private
    fun viewNode(node: FailureNode): View<Intent> = when (node) {
        is FailureNode.Property -> span(
            span(node.kind),
            reference(node.name),
            span(" of "),
            reference(node.owner)
        )
        is FailureNode.Task -> span(
            span("task"),
            reference(node.path),
            span(" of type "),
            reference(node.type)
        )
        is FailureNode.Bean -> span(
            span("bean of type "),
            reference(node.type)
        )
        is FailureNode.Label -> span(
            node.text
        )
        is FailureNode.Message -> viewPrettyText(
            node.prettyText
        )
        else -> span(
            node.toString()
        )
    }

    private
    fun viewLabel(
        treeIntent: (FailureTreeIntent) -> Intent,
        child: Tree.Focus<FailureNode>,
        label: FailureNode,
        decoration: View<Intent> = empty
    ): View<Intent> = div(
        treeButtonFor(child, treeIntent),
        decoration,
        span(" "),
        viewNode(label)
    )

    private
    fun treeButtonFor(child: Tree.Focus<FailureNode>, treeIntent: (FailureTreeIntent) -> Intent): View<Intent> =
        when {
            child.tree.isNotEmpty() -> viewTreeButton(child, treeIntent)
            else -> emptyTreeIcon
        }

    private
    fun viewTreeButton(child: Tree.Focus<FailureNode>, treeIntent: (FailureTreeIntent) -> Intent): View<Intent> = span(
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
        treeIntent: (FailureTreeIntent) -> Intent,
        child: Tree.Focus<FailureNode>,
        node: FailureNode.Exception
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
