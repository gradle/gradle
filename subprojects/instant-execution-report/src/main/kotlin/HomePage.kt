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
import elmish.attributes
import elmish.div
import elmish.h1
import elmish.h2
import elmish.ol
import elmish.tree.TreeView
import elmish.tree.viewChildrenOf


object HomePage : Component<HomePage.Model, HomePage.Intent> {

    data class Model(
        val totalFailures: Int,
        val messageTree: TreeView.Model<String>,
        val taskTree: TreeView.Model<String>
    )

    sealed class Intent {

        data class TaskTreeIntent(val delegate: TreeView.Intent<String>) : Intent()

        data class MessageTreeIntent(val delegate: TreeView.Intent<String>) : Intent()
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
            attributes {
                className("container")
            },
            viewTree(model.messageTree).map(Intent::MessageTreeIntent),
            viewTree(model.taskTree).map(Intent::TaskTreeIntent)
        )
    )

    private
    fun viewTree(model: TreeView.Model<String>): View<TreeView.Intent<String>> = div(
        h2(model.tree.label),
        ol(
            viewChildrenOf(model.tree.focus()) {
                val child = this
                div(
                    attributes {
                        if (child.tree.isNotEmpty()) {
                            className("accordion-header")
                            onClick { TreeView.Intent.Toggle(child) }
                        }
                    },
                    tree.label
                )
            }
        )
    )
}
