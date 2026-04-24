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

package org.gradle.kotlin.dsl.provider

import org.gradle.api.invocation.Gradle
import org.gradle.internal.InternalBuildAdapter
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.kotlin.dsl.support.cleanupKotlinCompilers
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment


/**
 * Disposes Kotlin compiler environment once all scripts are compiled.
 */
@ServiceScope(Scope.Build::class)
internal
class KotlinCompilerContextDisposer(
    private val listenerManager: ListenerManager
) : InternalBuildAdapter(), Stoppable {

    init {
        listenerManager.addListener(this)
    }

    override fun stop() {
        listenerManager.removeListener(this)
    }

    @OptIn(K1Deprecation::class)
    override fun projectsEvaluated(gradle: Gradle) { // TODO: If configuration fails (or in some tooling-api paths that don't reach projectsEvaluated), cleanup never fires. do these in the stop method instead?
        KotlinCoreEnvironment.disposeApplicationEnvironment()
        cleanupKotlinCompilers()
    }
}
