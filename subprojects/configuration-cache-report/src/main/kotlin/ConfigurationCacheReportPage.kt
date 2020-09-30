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
import elmish.ol
import elmish.pre
import elmish.small
import elmish.span
import elmish.tree.Tree
import elmish.tree.TreeView
import elmish.tree.viewSubTrees

import kotlinx.browser.window


internal
sealed class ProblemNode {

    data class Error(val label: ProblemNode, val docLink: ProblemNode?) : ProblemNode()

    data class Warning(val label: ProblemNode, val docLink: ProblemNode?) : ProblemNode()

    data class Task(val path: String, val type: String) : ProblemNode()

    data class Bean(val type: String) : ProblemNode()

    data class Property(val kind: String, val name: String, val owner: String) : ProblemNode()

    data class BuildLogic(val location: String) : ProblemNode()

    data class BuildLogicClass(val type: String) : ProblemNode()

    data class Label(val text: String) : ProblemNode()

    data class Link(val href: String, val label: String) : ProblemNode()

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
val ProblemTreeModel.problemCount: Int
    get() = tree.children.size


internal
object ConfigurationCacheReportPage : Component<ConfigurationCacheReportPage.Model, ConfigurationCacheReportPage.Intent> {

    data class Model(
        val cacheAction: String,
        val documentationLink: String,
        val totalProblems: Int,
        val messageTree: ProblemTreeModel,
        val locationTree: ProblemTreeModel,
        val displayFilter: DisplayFilter = DisplayFilter.All,
        val tab: Tab = Tab.ByMessage
    )

    enum class DisplayFilter {
        All, Errors, Warnings
    }

    enum class Tab(val text: String) {
        ByMessage("Problems grouped by message"),
        ByLocation("Problems grouped by location")
    }

    sealed class Intent {

        data class TaskTreeIntent(val delegate: ProblemTreeIntent) : Intent()

        data class MessageTreeIntent(val delegate: ProblemTreeIntent) : Intent()

        data class Copy(val text: String) : Intent()

        data class SetFilter(val displayFilter: DisplayFilter) : Intent()

        data class SetTab(val tab: Tab) : Intent()
    }

    override fun step(intent: Intent, model: Model): Model = when (intent) {
        is Intent.TaskTreeIntent -> model.copy(
            locationTree = TreeView.step(intent.delegate, model.locationTree)
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
        is Intent.SetTab -> model.copy(
            tab = intent.tab
        )
    }

    override fun view(model: Model): View<Intent> = div(
        attributes { className("report-wrapper") },
        div(
            attributes { className("header") },
            div(attributes { className("gradle-logo") }),
            learnMore(model.documentationLink),
            div(
                attributes { className("title") },
                h1("${model.totalProblems} problems were found ${model.cacheAction} the configuration cache"),
                div(
                    attributes { className("filters") },
                    div(
                        span("View"),
                        div(
                            attributes { className("filters-group") },
                            displayFilterButton(DisplayFilter.All, model.displayFilter),
                            displayFilterButton(DisplayFilter.Errors, model.displayFilter),
                            displayFilterButton(DisplayFilter.Warnings, model.displayFilter)
                        )
                    )
                )
            ),
            div(
                attributes { className("groups") },
                displayTabButton(Tab.ByMessage, model.tab, model.messageTree.problemCount),
                displayTabButton(Tab.ByLocation, model.tab, model.locationTree.problemCount)
            )
        ),
        div(
            attributes { className("content") },
            when (model.tab) {
                Tab.ByMessage -> viewTree(model.messageTree, Intent::MessageTreeIntent, model.displayFilter)
                Tab.ByLocation -> viewTree(model.locationTree, Intent::TaskTreeIntent, model.displayFilter)
            }
        )
    )

    private
    fun displayTabButton(tab: Tab, activeTab: Tab, problemsCount: Int): View<Intent> = div(
        attributes {
            className("group-selector")
            if (tab == activeTab) {
                className("group-selector--active")
            } else {
                onClick { Intent.SetTab(tab) }
            }
        },
        span(
            tab.text,
            span(
                attributes { className("group-selector__count") },
                "$problemsCount"
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
    fun learnMore(documentationLink: String): View<Intent> = div(
        attributes { className("learn-more") },
        span("Learn more about the "),
        a(
            attributes { href(documentationLink) },
            "Gradle Configuration Cache"
        ),
        span(".")
    )

    private
    fun viewTree(model: ProblemTreeModel, treeIntent: (ProblemTreeIntent) -> Intent, displayFilter: DisplayFilter): View<Intent> = div(
        ol(
            viewSubTrees(applyFilter(displayFilter, model)) { child ->
                when (val node = child.tree.label) {
                    is ProblemNode.Error -> {
                        viewLabel(treeIntent, child, node.label, node.docLink, errorIcon)
                    }
                    is ProblemNode.Warning -> {
                        viewLabel(treeIntent, child, node.label, node.docLink, warningIcon)
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
        is ProblemNode.BuildLogic -> span(
            span(node.location)
        )
        is ProblemNode.BuildLogicClass -> span(
            span("class "),
            reference(node.type)
        )
        is ProblemNode.Label -> span(
            node.text
        )
        is ProblemNode.Message -> viewPrettyText(
            node.prettyText
        )
        is ProblemNode.Link -> a(
            attributes {
                className("documentation-button")
                href(node.href)
            },
            node.label
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
        docLink: ProblemNode? = null,
        decoration: View<Intent> = empty
    ): View<Intent> = div(
        listOf(
            treeButtonFor(child, treeIntent),
            decoration,
            viewNode(label)
        ) + if (docLink == null) {
            emptyList()
        } else {
            listOf(viewNode(docLink))
        })

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
            if (child.tree.state === Tree.ViewState.Collapsed) {
                className("collapsed")
            }
            if (child.tree.state === Tree.ViewState.Expanded) {
                className("expanded")
            }
            title("Click to ${toggleVerb(child.tree.state)}")
            onClick { treeIntent(TreeView.Intent.Toggle(child)) }
        },
        when (child.tree.state) {
            Tree.ViewState.Collapsed -> "› "
            Tree.ViewState.Expanded -> "⌄ "
        }
    )

    private
    val errorIcon = span<Intent>(
        attributes { className("error-icon") },
        "⨉"
    )

    private
    val warningIcon = span<Intent>(
        attributes { className("warning-icon") },
        "⚠️"
    )

    private
    val emptyTreeIcon = span<Intent>(
        attributes { className("tree-icon") },
        "■"
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
