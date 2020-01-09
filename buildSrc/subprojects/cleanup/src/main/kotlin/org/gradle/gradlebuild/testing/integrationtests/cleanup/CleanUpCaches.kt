/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.gradlebuild.testing.integrationtests.cleanup

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GradleVersion
import java.io.File
import javax.inject.Inject


abstract class CleanUpCaches : DefaultTask() {
    @get:Internal
    abstract val version: Property<GradleVersion>

    @get:Internal
    abstract val homeDir: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun cleanUpCaches() {

        // Expire intTestImage cache snapshots that are older than the tested version
        // Also expire version-specific cache snapshots when they can't be re-used (for 'snapshot-1' developer builds)
        val expireIntegTestCache = Spec<GradleVersion> { candidateVersion ->
            (candidateVersion.isSnapshot && candidateVersion < version.get())
                || candidateVersion.version.endsWith("-snapshot-1")
        }

        val homeDir = homeDir.get()
        val workerDir = homeDir.dir("worker-1")

        // Remove state for old versions of Gradle that we're unlikely to ever require again
        fileSystemOperations.removeOldVersionsFromDir(workerDir.dir("caches"), expireIntegTestCache)

        /*
         intTestHomeDir
            generatedApiJars
                5.0-123123123123
                    core-api_AZERA        <-- the system property to pass to generated jar cache
         */
        fileSystemOperations.removeOldVersionsFromDir(homeDir.dir("generatedApiJars"), expireIntegTestCache)

        // Remove scripts caches
        fileSystemOperations.removeCachedScripts(workerDir.dir("caches").asFile)

        // Remove script caches from TestKit integTest temp dir
        // location defined in TempTestKitDirProvider, copied here
        val testKitTmpDir = File(File(System.getProperty("java.io.tmpdir")), String.format(".gradle-test-kit-%s", System.getProperty("user.name")))
        fileSystemOperations.removeCachedScripts(File(testKitTmpDir, "caches"))
        fileSystemOperations.removeTransformDir(File(testKitTmpDir, "caches"))

        fileSystemOperations.removeOldVersionsFromDir(workerDir.dir("daemon"), expireIntegTestCache)

        // Remove old distributions used by wrapper that we're unlikely to ever require again
        fileSystemOperations.removeOldVersionsFromDir(workerDir.dir("wrapper/dists"), expireIntegTestCache, "gradle-", "-bin")
        fileSystemOperations.delete {
            delete(workerDir.dir("wrapper/dists/dist"))
        }

        // Remove caches that weren't multi-process safe and may be corrupt
        fileSystemOperations.removeDodgyCacheFiles(workerDir.dir("caches"))

        // Remove old daemon log files
        fileSystemOperations.removeDaemonLogFiles(homeDir.dir("worker-1/daemon"))
    }
}
