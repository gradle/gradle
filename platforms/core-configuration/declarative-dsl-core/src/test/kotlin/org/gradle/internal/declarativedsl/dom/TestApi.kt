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
import org.gradle.declarative.dsl.model.annotations.Restricted


@Suppress("unused")
class TestApi {

    interface TopLevelReceiver {

        @Adding
        fun addAndConfigure(name: String, configure: TopLevelElement.() -> Unit): TopLevelElement

        @get:Restricted
        var complexValueOne: ComplexValueOne

        @get:Restricted
        var complexValueOneFromUtils: ComplexValueOne

        @get:Restricted
        var complexValueTwo: ComplexValueTwo

        @Adding
        fun justAdd(name: String): TopLevelElement

        @Configuring
        fun nested(configure: NestedReceiver.() -> Unit)

        @Restricted
        fun one(complexValueTwo: ComplexValueTwo): ComplexValueOne

        @Restricted
        fun two(name: String): ComplexValueTwo

        @get:Restricted
        val utils: Utils
    }

    class ComplexValueOne

    class ComplexValueTwo

    interface TopLevelElement {
        @get:Restricted
        var name: String

        @get:Restricted
        var number: Int
    }

    interface NestedReceiver {
        @get:Restricted
        var number: Int

        @get:Restricted
        var otherNumber: Int

        @get:Restricted
        var enum: Enum

        @Adding
        fun add(): MyNestedElement

        @get:Restricted
        var listOfComplexValueOne: List<ComplexValueOne>

        @Restricted
        fun <T> mySingletonList(t: T): List<T>

        @Configuring
        fun configure(configure: NestedReceiver.() -> Unit)
    }

    interface Utils {
        @Restricted
        fun oneUtil(): ComplexValueOne
    }

    interface MyNestedElement

    enum class Enum {
        A, B, C
    }
}


