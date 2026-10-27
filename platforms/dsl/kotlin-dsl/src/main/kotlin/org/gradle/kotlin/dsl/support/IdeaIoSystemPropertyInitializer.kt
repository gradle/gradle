/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.support

import org.gradle.internal.buildtree.BuildTreeLifecycleListener
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


/**
 * Sets `idea.io.use.nio2`, which speeds up file I/O in the Kotlin compiler for `.gradle.kts` scripts.
 *
 * Done eagerly rather than lazily during compilation, so its value stays stable for the configuration cache across
 * builds that compile scripts and builds that reuse already-compiled ones.
 */
@ServiceScope(Scope.BuildTree::class)
internal
class IdeaIoSystemPropertyInitializer : BuildTreeLifecycleListener {

    override fun afterStart() {
        org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback()
    }
}
