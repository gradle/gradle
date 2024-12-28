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

package org.gradle.internal.isolation.actions

import org.gradle.api.Action
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.model.ModelContainer
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.serviceOf

class DefaultActionIsolator(owner: IsolateOwner) : ActionIsolator {

    private val serializer: IsolatedActionSerializer
    private val deserializer: IsolatedActionDeserializer

    init {
        val isolatedActionCodecs = owner.serviceOf<IsolatedActionCodecsFactory>()
        serializer = IsolatedActionSerializer(owner, owner.serviceOf(), isolatedActionCodecs)
        deserializer = IsolatedActionDeserializer(owner, owner.serviceOf(), isolatedActionCodecs)
    }

    override fun <T : Any?> isolateLenient(action: Action<T>, actionType: String, model: ModelContainer<*>): ThreadSafeAction<T> =
        serializer.trySerialize(action).let { result ->
            return when (result) {
                is TrySerialize.Success<Action<T>> -> {
                    ThreadSafeAction { param ->
                        deserializer.deserialize(result.value).execute(param)
                    }
                }
                is TrySerialize.Failure<*> -> {
                    val problemMessage = result.problems.run {
                        when (size) {
                            1 -> first().message.toString()
                            else -> "[${joinToString { it.message.toString() }}]"
                        }
                    }

                    DeprecationLogger.deprecateBehaviour("Failed to isolate $actionType from project context: $problemMessage")
                        .withContext("$actionType actions must not reference project state.")
                        .willBecomeAnErrorInGradle10()
                        .undocumented()
                        .nagUser()

                    ThreadSafeAction { param ->
                        model.fromMutableState { action.execute(param) }
                    }
                }
            }
        }

}
