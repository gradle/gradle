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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.internal.tasks.WorkNodeAction
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.withCodec
import org.gradle.execution.plan.ActionNode


class ActionNodeCodec(
    private val userTypesCodec: Codec<Any?>,
) : Codec<ActionNode> {
    override suspend fun WriteContext.encode(value: ActionNode) {
        withCodec(userTypesCodec) {
            write(value.action)
        }
    }

    override suspend fun ReadContext.decode(): ActionNode {
        val action = withCodec(userTypesCodec) { readNonNull<WorkNodeAction>() }
        return ActionNode(action)
    }
}
