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


@Suppress("unused")
class TestApi {

    interface TopLevelReceiver {

        @Adding
        fun addAndConfigure(name: String, configure: TopLevelElement.() -> Unit): TopLevelElement

        var complexValueOne: ComplexValueOne

        var complexValueOneFromUtils: ComplexValueOne

        var complexValueTwo: ComplexValueTwo

        @Adding
        fun justAdd(name: String): TopLevelElement

        fun nested(configure: NestedReceiver.() -> Unit)

        fun one(complexValueTwo: ComplexValueTwo): ComplexValueOne

        fun two(name: String): ComplexValueTwo

        val utils: Utils
    }

    class ComplexValueOne

    class ComplexValueTwo

    interface TopLevelElement {
        var name: String

        var number: Int
    }

    interface NestedReceiver {
        var number: Int

        var otherNumber: Int

        var enum: Enum

        @Adding
        fun add(): MyNestedElement

        var listOfComplexValueOne: List<ComplexValueOne>

        fun <T> mySingletonList(t: T): List<T>

        fun configure(configure: NestedReceiver.() -> Unit)
    }

    interface Utils {
        fun oneUtil(): ComplexValueOne
    }

    interface MyNestedElement

    enum class Enum {
        A, B, C
    }
}


