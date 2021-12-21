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

import org.gradle.configurationcache.initialization.ConfigurationCacheProblemsListener
import org.gradle.configurationcache.serialization.Workarounds
import org.gradle.internal.classpath.Instrumented
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


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


@ServiceScope(Scopes.BuildTree::class)
class InstrumentedInputAccessListener(
    listenerManager: ListenerManager,
    configurationCacheProblemsListener: ConfigurationCacheProblemsListener,
) : Instrumented.Listener {

    private
    val broadcast = listenerManager.getBroadcaster(UndeclaredBuildInputListener::class.java)

    private
    val externalProcessListener = configurationCacheProblemsListener

    override fun systemPropertyQueried(key: String, value: Any?, consumer: String) {
        if (allowedProperties.contains(key) || Workarounds.canReadSystemProperty(consumer)) {
            return
        }
        broadcast.systemPropertyRead(key, value, consumer)
    }

    override fun envVariableQueried(key: String, value: String?, consumer: String) {
        if (Workarounds.canReadEnvironmentVariable(consumer)) {
            return
        }
        broadcast.envVariableRead(key, value, consumer)
    }

    override fun externalProcessStarted(command: String, consumer: String?) {
        externalProcessListener.onExternalProcessStarted(command, consumer)
    }
}
