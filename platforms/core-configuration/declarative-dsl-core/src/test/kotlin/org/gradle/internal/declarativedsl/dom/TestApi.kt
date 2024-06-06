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

package org.gradle.internal.declarativedsl.dom

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.HiddenInDeclarativeDsl
import org.gradle.declarative.dsl.model.annotations.Restricted


@Suppress("unused", "UNUSED_PARAMETER")
class TestApi {

    class TopLevelReceiver {

        @Adding
        fun addAndConfigure(name: String, configure: TopLevelElement.() -> Unit) = TopLevelElement().also {
            it.name = name
            configure(it)
        }

        @get:Restricted
        lateinit var complexValueOne: ComplexValueOne

        @get:Restricted
        lateinit var complexValueOneFromUtils: ComplexValueOne

        @get:Restricted
        lateinit var complexValueTwo: ComplexValueTwo

        @Adding
        fun justAdd(name: String): TopLevelElement = TopLevelElement()

        @Configuring
        fun nested(configure: NestedReceiver.() -> Unit) = configure(nested)

        @Restricted
        fun one(complexValueTwo: ComplexValueTwo): ComplexValueOne = ComplexValueOne()

        @Restricted
        fun two(name: String): ComplexValueTwo = ComplexValueTwo()

        @get:Restricted
        val utils: Utils = Utils()

        @get:Restricted
        @get:HiddenInDeclarativeDsl
        val nested = NestedReceiver()
    }

    class ComplexValueOne

    class ComplexValueTwo

    @Suppress("unused")
    class TopLevelElement {
        @get:Restricted
        var name: String = ""

        @get:Restricted
        var number: Int = 0
    }

    @Suppress("unused")
    class NestedReceiver {
        @get:Restricted
        var number: Int = 0

        @Adding
        fun add(): MyNestedElement = MyNestedElement()
    }

    class Utils {
        @Restricted
        fun oneUtil(): ComplexValueOne = ComplexValueOne()
    }

    class MyNestedElement
}


