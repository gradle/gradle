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

package org.gradle.instantexecution

import org.gradle.api.Task
import org.slf4j.Logger
import java.util.IdentityHashMap


class SerializationContext(owner: Task, logger: Logger) : StateContext(owner, logger) {
    private
    val instanceIds = IdentityHashMap<Any, Int>()

    fun getId(instance: Any) = instanceIds[instance]

    fun putInstance(instance: Any): Int {
        val id = instanceIds.size
        instanceIds[instance] = id
        return id
    }
}
