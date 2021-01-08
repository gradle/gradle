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

import org.w3c.dom.Element

import kotlinx.browser.document


/**
 * An user interface component built according to [the Elm Architecture](https://guide.elm-lang.org/architecture/)
 * pattern.
 *
 * @param M the model type (also called the state type)
 * @param I the intent type (also called the update, action or message type)
 */
interface Component<M, I> {

    fun view(model: M): View<I>

    fun step(intent: I, model: M): M
}


/**
 * Defines a [Component] given its [view] and [step] functions.
 *
 * ```
 *  mountComponentAt(
 *    elementById("playground"),
 *    component<Int, Unit>(
 *      view = { model ->
 *        span(
 *          attributes {
 *            onClick { Unit }
 *          },
 *          "You clicked me $model time(s)."
 *        )
 *      },
 *      step = { _, model ->
 *        model + 1
 *      }
 *    ),
 *    0
 *  )
 * ```
 */
fun <M, I> component(view: (M) -> View<I>, step: (I, M) -> M): Component<M, I> = object : Component<M, I> {
    override fun view(model: M): View<I> = view(model)
    override fun step(intent: I, model: M): M = step(intent, model)
}


fun <M, I> mountComponentAt(
    element: Element,
    component: Component<M, I>,
    init: M
) {
    fun loop(model: M) {
        render(
            component.view(model),
            into = element
        ) { intent -> loop(component.step(intent, model)) }
    }

    loop(init)

    println("Component mounted at #${element.id}.")
}


fun elementById(id: String): Element =
    document.getElementById(id) ?: throw IllegalStateException("'$id' element missing")
