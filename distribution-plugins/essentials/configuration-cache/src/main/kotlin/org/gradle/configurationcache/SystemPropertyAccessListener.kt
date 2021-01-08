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

package org.gradle.configurationcache

import org.gradle.api.InvalidUserCodeException
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.configurationcache.problems.DocumentationSection.RequirementsUndeclaredSysPropRead
import org.gradle.configurationcache.problems.ProblemsListener
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.problems.StructuredMessage
import org.gradle.configurationcache.problems.location
import org.gradle.configurationcache.serialization.Workarounds
import org.gradle.internal.classpath.Instrumented
import org.gradle.internal.event.ListenerManager


private
val allowedProperties = setOf(
    "os.name",
    "os.version",
    "os.arch",
    "java.version",
    "java.version.date",
    "java.vendor",
    "java.vendor.url",
    "java.vendor.version",
    "java.specification.version",
    "java.specification.vendor",
    "java.specification.name",
    "java.vm.version",
    "java.vm.specification.version",
    "java.vm.specification.vendor",
    "java.vm.specification.name",
    "java.vm.version",
    "java.vm.vendor",
    "java.vm.name",
    "java.class.version",
    "java.home",
    "java.class.path",
    "java.library.path",
    "java.compiler",
    "file.separator",
    "path.separator",
    "line.separator",
    "user.name",
    "user.home",
    "java.runtime.version"
    // Not java.io.tmpdir and user.dir at this stage
)


class SystemPropertyAccessListener(
    private val problems: ProblemsListener,
    private val userCodeContext: UserCodeApplicationContext,
    listenerManager: ListenerManager
) : Instrumented.Listener {
    private
    val broadcast = listenerManager.getBroadcaster(UndeclaredBuildInputListener::class.java)

    private
    val nullProperties = mutableSetOf<String>()

    override fun systemPropertyQueried(key: String, value: Any?, consumer: String) {
        if (allowedProperties.contains(key) || Workarounds.canReadSystemProperty(consumer)) {
            return
        }
        if (value == null) {
            if (nullProperties.add(key)) {
                broadcast.systemPropertyRead(key)
            }
            return
        }
        val message = StructuredMessage.build {
            text("read system property ")
            reference(key)
        }
        val location = userCodeContext.location(consumer)
        val exception = InvalidUserCodeException(message.toString().capitalize())
        problems.onProblem(
            PropertyProblem(
                location,
                message,
                exception,
                documentationSection = RequirementsUndeclaredSysPropRead
            )
        )
    }
}
