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
import elmish.div
import elmish.h1
import elmish.h2
import elmish.ol
import elmish.pre
import elmish.span
import elmish.tree.Tree
import elmish.tree.TreeView
import elmish.tree.viewChildrenOf


sealed class FailureNode {

    data class Label(val text: String) : FailureNode()

    data class Error(val message: String) : FailureNode()

    data class Warning(val message: String) : FailureNode()

    data class Exception(val message: String, val stackTrace: String) : FailureNode()
}


typealias FailureTreeModel = TreeView.Model<FailureNode>


typealias FailureTreeIntent = TreeView.Intent<FailureNode>


object HomePage : Component<HomePage.Model, HomePage.Intent> {

    data class Model(
        val totalFailures: Int,
        val messageTree: FailureTreeModel,
        val taskTree: FailureTreeModel
    )

    sealed class Intent {

        data class TaskTreeIntent(val delegate: FailureTreeIntent) : Intent()

        data class MessageTreeIntent(val delegate: FailureTreeIntent) : Intent()
    }

    override fun step(intent: Intent, model: Model): Model = when (intent) {
        is Intent.TaskTreeIntent -> model.copy(
            taskTree = TreeView.step(intent.delegate, model.taskTree)
        )
        is Intent.MessageTreeIntent -> model.copy(
            messageTree = TreeView.step(intent.delegate, model.messageTree)
        )
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
            viewTree(model.messageTree).map(Intent::MessageTreeIntent),
            viewTree(model.taskTree).map(Intent::TaskTreeIntent)
        )
    )

    private
    fun viewTree(model: FailureTreeModel): View<FailureTreeIntent> = div(
        h2(model.tree.label.unsafeCast<FailureNode.Label>().text),
        ol(
            viewChildrenOf(model.tree.focus()) { child ->
                when (val node = child.tree.label) {
                    is FailureNode.Error -> {
                        viewLabel(child, node.message + " ❌")
                    }
                    is FailureNode.Warning -> {
                        viewLabel(child, node.message + " ⚠️")
                    }
                    is FailureNode.Label -> {
                        viewLabel(child, node.text)
                    }
                    is FailureNode.Exception -> {
                        viewException(node)
                    }
                }
            }
        )
    )

    private
    fun viewLabel(child: Tree.Focus<FailureNode>, text: String): View<FailureTreeIntent> =
        div(
            attributes {
                if (child.tree.isNotEmpty()) {
                    className("accordion-header")
                    title("Click to ${toggleVerb(child.tree.state)}")
                    onClick { TreeView.Intent.Toggle(child) }
                }
            },
            span(text)
        )

    private
    fun viewException(node: FailureNode.Exception): View<FailureTreeIntent> =
        div(
            span(node.message),
            pre(
                attributes { className("screen") },
                node.stackTrace
            )
        )

    private
    fun toggleVerb(state: Tree.ViewState): String = when (state) {
        Tree.ViewState.Collapsed -> "expand"
        Tree.ViewState.Expanded -> "collapse"
    }
}
