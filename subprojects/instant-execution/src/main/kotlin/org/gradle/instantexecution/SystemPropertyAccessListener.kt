/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.instantexecution.problems.InstantExecutionProblems
import org.gradle.instantexecution.problems.PropertyProblem
import org.gradle.instantexecution.problems.PropertyTrace
import org.gradle.instantexecution.problems.StructuredMessage
import org.gradle.instantexecution.serialization.Workarounds
import org.gradle.internal.classpath.Instrumented


class SystemPropertyAccessListener(
    private val problems: InstantExecutionProblems
) : Instrumented.Listener {
    private
    val whitelistedProperties = setOf(
        "os.name",
        "os.version",
        "java.version",
        "java.vm.version",
        "java.runtime.version",
        "java.specification.version",
        "java.home",
        "line.separator",
        "user.name",
        "user.home"
    )

    override fun systemPropertyQueried(key: String, consumer: String) {
        if (whitelistedProperties.contains(key) || Workarounds.canReadSystemProperty(consumer)) {
            return
        }
        val message = StructuredMessage.build {
            text("read system property '")
            text(key)
            text("' from ")
            reference(consumer)
        }
        problems.onProblem(PropertyProblem(PropertyTrace.Unknown, message))
    }
}
