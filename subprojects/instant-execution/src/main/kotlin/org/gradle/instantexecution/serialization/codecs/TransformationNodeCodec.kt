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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.api.Project
import org.gradle.api.internal.tasks.WorkNodeAction
import org.gradle.execution.plan.ActionNode
import org.gradle.execution.plan.Node
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.service.ServiceRegistry


internal
object TransformationNodeCodec : Codec<Node> {
    override suspend fun WriteContext.encode(value: Node) {
        val id = sharedIdentities.getId(value)
        if (id != null) {
            writeSmallInt(id)
        } else {
            writeSmallInt(sharedIdentities.putInstance(value))
            // Ignore the state
        }
    }

    override suspend fun ReadContext.decode(): Node {
        val id = readSmallInt()
        val instance = sharedIdentities.getInstance(id)
        if (instance != null) {
            return instance as Node
        }
        // Return a dummy node that does not do anything
        val node = ActionNode(object : WorkNodeAction {
            override fun getProject(): Project? {
                return null
            }

            override fun run(registry: ServiceRegistry) {
            }
        })
        sharedIdentities.putInstance(id, node)
        return node
    }
}
