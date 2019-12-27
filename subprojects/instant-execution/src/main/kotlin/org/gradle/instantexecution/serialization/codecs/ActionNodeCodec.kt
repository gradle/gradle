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
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.WorkNodeAction
import org.gradle.execution.plan.ActionNode
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.logNotImplemented


object ActionNodeCodec : Codec<ActionNode> {
    override suspend fun WriteContext.encode(value: ActionNode) {
        if (value.action is DefaultConfiguration.ResolveGraphAction) {
            // Can ignore
            return
        } else {
            logNotImplemented(value.action.javaClass)
        }
    }

    override suspend fun ReadContext.decode(): ActionNode? {
        // TODO - should discard from graph instead
        return ActionNode(object : WorkNodeAction {
            override fun run(context: NodeExecutionContext) {
                // Ignore
            }

            override fun getProject(): Project? {
                return null
            }
        })
    }
}
