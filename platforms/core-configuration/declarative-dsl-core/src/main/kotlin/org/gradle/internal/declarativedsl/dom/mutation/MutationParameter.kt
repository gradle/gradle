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

package org.gradle.internal.declarativedsl.dom.mutation

import java.util.Collections


data class MutationParameter<T : Any>(
    val name: String,
    val description: String,
    val kind: MutationParameterKind<T>
)


sealed interface MutationParameterKind<T : Any> {
    data object IntParameter : MutationParameterKind<Int>
    data object StringParameter : MutationParameterKind<String>
    data object BooleanParameter : MutationParameterKind<Boolean>
}


interface MutationArgumentContainer {
    operator fun <T : Any> get(parameter: MutationParameter<T>): T
    val allArguments: Map<MutationParameter<*>, Any>

    interface Builder {
        fun <T : Any> argument(parameter: MutationParameter<T>, value: T)
    }
}


fun mutationArguments(buildArguments: MutationArgumentContainer.Builder.() -> Unit): MutationArgumentContainer {
    val args = mutableMapOf<MutationParameter<*>, Any>()
    buildArguments(object : MutationArgumentContainer.Builder {
        override fun <T : Any> argument(parameter: MutationParameter<T>, value: T) {
            args[parameter] = value
        }
    })
    return DefaultMutationArgumentContainer(Collections.unmodifiableMap(args))
}


internal
class DefaultMutationArgumentContainer(
    override val allArguments: Map<MutationParameter<*>, Any>
) : MutationArgumentContainer {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(parameter: MutationParameter<T>): T = allArguments.get(parameter) as T
}
