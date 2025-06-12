/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.base.serialize

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.internal.state.ModelObject
import org.gradle.util.Path

class RuntimeTaskCheckingPropertyHost(val taskPath: Path, val project: ProjectInternal) : PropertyHost {
    override fun beforeRead(producer: ModelObject?): String? {
        try {
            val producerTask = project.tasks.resolveTask(taskPath.toString()) as TaskInternal
            if (producerTask.getState().isConfigurable()) {
                // Currently cannot tell the difference between access from the producing task and access from outside, so assume
                // all access after the task has started execution is ok
                return producerTask.toString() + " has not completed yet"
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        return null
    }
}
