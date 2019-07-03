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
import elmish.tree.viewChildrenOf

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
        val taskTree: FailureTreeModel
    )

    sealed class Intent {

        data class TaskTreeIntent(val delegate: FailureTreeIntent) : Intent()

        data class MessageTreeIntent(val delegate: FailureTreeIntent) : Intent()

        data class Copy(val text: String) : Intent()
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
    }

    override fun view(model: Model): View<Intent> = div(
        h1("${model.totalFailures} instant execution failures"),
        div(
            attributes { className("container") },
            span("Learn more about "),
            a(
                attributes { href("https://gradle.github.io/instant-execution/") },
                "Gradle Instant Execution"
            ),
            span(".")
        ),
        div(
            attributes { className("container") },
            viewTree(model.messageTree, Intent::MessageTreeIntent),
            viewTree(model.taskTree, Intent::TaskTreeIntent)
        )
    )

    private
    fun viewTree(model: FailureTreeModel, treeIntent: (FailureTreeIntent) -> Intent): View<Intent> = div(
        h2(model.tree.label.unsafeCast<FailureNode.Label>().text),
        ol(
            viewChildrenOf(model.tree.focus()) { child ->
                when (val node = child.tree.label) {
                    is FailureNode.Error -> {
                        viewLabel(treeIntent, child, node.label, errorDecoration)
                    }
                    is FailureNode.Warning -> {
                        viewLabel(treeIntent, child, node.label, warningDecoration)
                    }
                    is FailureNode.Exception -> {
                        viewException(node)
                    }
                    else -> {
                        viewLabel(treeIntent, child, node)
                    }
                }
            }
        )
    )

    private
    val errorDecoration = span(" ❌")

    private
    val warningDecoration = span(" ⚠️")

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
        attributes {
            if (child.tree.isNotEmpty()) {
                className("accordion-header")
                title("Click to ${toggleVerb(child.tree.state)}")
                onClick { treeIntent(TreeView.Intent.Toggle(child)) }
            }
        },
        viewNode(label),
        decoration
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
    fun viewException(node: FailureNode.Exception): View<Intent> =
        div(
            span("exception stack trace "),
            copyButton(
                text = node.stackTrace,
                tooltip = "Copy original stacktrace to the clipboard"
            ),
            pre(
                attributes { className("stacktrace") },
                node.stackTrace
            )
        )

    private
    fun toggleVerb(state: Tree.ViewState): String = when (state) {
        Tree.ViewState.Collapsed -> "expand"
        Tree.ViewState.Expanded -> "collapse"
    }
}
