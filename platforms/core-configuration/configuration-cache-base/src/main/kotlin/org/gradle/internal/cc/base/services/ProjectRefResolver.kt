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

package org.gradle.internal.cc.base.services

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.build.BuildState
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import kotlin.jvm.Throws

/**
 * A stateful service that prevents races in `readProjectRef` when looking up projects by their paths.
 * Threads may opt into waiting the until the project structure is restored when trying to resolve a project reference by calling [withWaitingForProjectsAllowed].
 * Unless projects are loaded, an attempt to resolve a project without the opt-in throws an exception to avoid deadlocks.
 *
 * Using this class should be limited to serialization.
 *
 * This class is thread-safe.
 */
@ServiceScope(Scope.Build::class)
class ProjectRefResolver @Inject constructor(
    private val buildState: BuildState
) {
    private val projectReadyLock = CountDownLatch(1)

    @Volatile
    private var projectsLoaded = false

    private val waitingForProjectsAllowed: MutableSet<Thread> = ConcurrentHashMap.newKeySet()

    /**
     * Allows this thread to wait for the project structure to be restored when calling [getProject] inside `block`.
     *
     * The thread that is responsible for loading the projects mustn't call this method to help detect potential deadlocks.
     */
    fun withWaitingForProjectsAllowed(block: () -> Unit) {
        val thread = Thread.currentThread()

        val wasFirstToAdd = waitingForProjectsAllowed.add(thread)
        try {
            block()
        } finally {
            if (wasFirstToAdd) {
                waitingForProjectsAllowed.remove(thread)
            }
        }
    }

    /**
     * Marks projects as restored.
     */
    fun projectsReady() {
        projectsLoaded = true
        projectReadyLock.countDown()
    }

    /**
     * Resolves a project by a path relative to the root project or blocks until the projects are loaded if allowed.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws TimeoutException if the projects weren't loaded within a reasonable time
     * @throws IllegalStateException if the projects aren't ready and this thread isn't allowed to wait for them
     */
    @Throws(InterruptedException::class, TimeoutException::class)
    fun getProject(path: String): ProjectInternal {
        waitForProjects(path)

        return buildState.projects.getProject(Path.path(path)).mutableModel
    }

    private fun waitForProjects(projectPath: String) {
        if (projectsLoaded) {
            return
        }

        check(waitingForProjectsAllowed.contains(Thread.currentThread())) {
            // This may happen if something that requires a project is deserialized before the project structure is ready.
            "Cannot resolve project path '$projectPath' because project structure is not yet loaded for build '${buildState.identityPath}'"
        }

        if (!projectReadyLock.await(5, TimeUnit.MINUTES)) {
            // We're typically wait here from a shared object decoder thread.
            // It may take some time for the main decoding logic to get to the actual projects and restore them, so this timeout should be generous.
            // TODO(mlopatkin) a deadlock is still possible when the main decoder waits for some shared object and the shared decoder is stuck waiting for projects.
            //   The blocked main thread is likely to give up earlier anyway.
            throw TimeoutException("Cannot resolve project path '$projectPath' because project structure is not ready in time for build '${buildState.identityPath}'")
        }
    }
}
