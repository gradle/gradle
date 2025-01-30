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

package org.gradle.internal.cc.impl.services

import org.gradle.api.internal.GradleInternal
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


/**
 * Access to the root build's [GradleInternal] instance
 * that becomes available as soon as the root build state is created.
 */
@ServiceScope(Scope.BuildTree::class)
internal
interface DeferredRootBuildGradle {

    /**
     * Returns the root build's [GradleInternal] instance.
     *
     * @throws IllegalStateException if the root build is not yet initialized
     */
    val gradle: GradleInternal
}


internal
class DefaultDeferredRootBuildGradle : DeferredRootBuildGradle {

    private var rootBuildGradle: GradleInternal? = null

    override val gradle: GradleInternal
        get() = rootBuildGradle ?: error("Root build Gradle is not available")

    fun attach(rootBuildGradle: GradleInternal) {
        check(this.rootBuildGradle == null) { "Cannot re-attach root build Gradle" }

        // The reference will be kept until the end of the build-tree lifetime
        this.rootBuildGradle = rootBuildGradle
    }
}
