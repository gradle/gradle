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

package elmish

import kotlinx.dom.addClass
import kotlinx.dom.appendElement
import kotlinx.dom.appendText
import org.w3c.dom.Element
import org.w3c.dom.events.Event


val empty = View.Empty


val hr = ViewFactory("hr")


val h1 = ViewFactory("h1")


val h2 = ViewFactory("h2")


val div = ViewFactory("div")


val pre = ViewFactory("pre")


val code = ViewFactory("code")


val span = ViewFactory("span")


val small = ViewFactory("small")


val ol = ViewFactory("ol")


val ul = ViewFactory("ul")


val li = ViewFactory("li")


val a = ViewFactory("a")


val br = ViewFactory("br")


fun <I> render(view: View<I>, into: Element, send: (I) -> Unit) {
    into.innerHTML = ""
    into.appendElementFor(view, send)
}


data class ViewFactory(val elementName: String) {

    operator fun <I> invoke(innerText: String): View<I> =
        View(elementName, innerText = innerText)

    operator fun <I> invoke(children: List<View<I>>): View<I> =
        View(elementName, children = children)

    operator fun <I> invoke(vararg children: View<I>): View<I> =
        View(elementName, children = children.asList())

    operator fun <I> invoke(
        attributes: List<Attribute<I>>,
        vararg children: View<I>
    ): View<I> = View(
        elementName,
        attributes = attributes,
        children = children.asList()
    )

    operator fun <I> invoke(
        attributes: List<Attribute<I>>,
        innerText: String
    ): View<I> = View(
        elementName,
        attributes = attributes,
        innerText = innerText
    )

    operator fun <I> invoke(
        innerText: String,
        vararg children: View<I>
    ): View<I> = View(
        elementName,
        innerText = innerText,
        children = children.asList()
    )
}


/**
 * A minimalist HTML data structure for type-safe composition of fragments.
 */
sealed class View<out I> {

    fun <O> map(f: (I) -> O): View<O> =
        MappedView(this, f)

    companion object {

        operator fun <I> invoke(
            elementName: String,
            attributes: List<Attribute<out I>> = emptyList(),
            innerText: String? = null,
            children: List<View<I>> = emptyList()
        ): View<I> = Element(
            elementName,
            attributes,
            innerText,
            children
        )
    }

    object Empty : View<Nothing>()

    data class Element<out I>(
        val elementName: String,
        val attributes: List<Attribute<out I>> = emptyList(),
        val innerText: String? = null,
        val children: List<View<I>> = emptyList()
    ) : View<I>()

    data class MappedView<I, out O>(
        val view: View<I>,
        val mapping: (I) -> O
    ) : View<O>()
}


fun <I> attributes(builder: Attributes<I>.() -> Unit): List<Attribute<I>> =
    mutableListOf<Attribute<I>>().also { attributes ->
        builder(Attributes { attributes.add(it) })
    }


class Attributes<I>(private val add: (Attribute<I>) -> Unit) {

    fun onClick(handler: EventHandler<I>) = add(Attribute.OnEvent("click", handler))

    fun className(value: String) = add(Attribute.ClassName(value))

    fun title(value: String) = add(Attribute.Named("title", value))

    fun href(value: String) = add(Attribute.Named("href", value))

    fun src(value: String) = add(Attribute.Named("src", value))
}


typealias EventHandler<I> = (Event) -> I


sealed class Attribute<I> {

    class OnEvent<I>(
        val eventName: String,
        val handler: (Event) -> I
    ) : Attribute<I>()

    class ClassName<I>(
        val value: String
    ) : Attribute<I>()

    class Named<I>(
        val name: String,
        val value: String
    ) : Attribute<I>()
}


private
fun <I> Element.appendElementFor(view: View<I>, send: (I) -> Unit) {
    when (view) {
        is View.Element -> appendElement(view.elementName) {
            view.attributes.forEach {
                setAttribute(it, send)
            }
            view.innerText?.let(this::appendText)
            view.children.forEach { childView ->
                appendElementFor(childView, send)
            }
        }
        is View.MappedView<*, *> -> {
            @Suppress("unchecked_cast")
            val mappedView = view as View.MappedView<Any?, I>
            appendElementFor(mappedView.view) {
                send(mappedView.mapping(it))
            }
        }
        View.Empty -> return
    }
}


private
fun <I> Element.setAttribute(attr: Attribute<out I>, send: (I) -> Unit) {
    when (attr) {
        is Attribute.Named -> setAttribute(attr.name, attr.value)
        is Attribute.ClassName -> addClass(attr.value)
        is Attribute.OnEvent -> addEventListener(
            attr.eventName,
            { event ->
                event.stopPropagation()
                send(attr.handler(event))
            }
        )
    }
}
