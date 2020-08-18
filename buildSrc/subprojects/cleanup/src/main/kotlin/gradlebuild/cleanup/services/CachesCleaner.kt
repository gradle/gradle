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

package gradlebuild.cleanup.services

import gradlebuild.cleanup.removeCachedScripts
import gradlebuild.cleanup.removeDaemonLogFiles
import gradlebuild.cleanup.removeDodgyCacheFiles
import gradlebuild.cleanup.removeOldVersionsFromDir
import gradlebuild.cleanup.removeTransformDir
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.specs.Spec
import org.gradle.util.GradleVersion
import java.io.File
import javax.inject.Inject


abstract class CachesCleaner : BuildService<CachesCleaner.Params> {
    interface Params : BuildServiceParameters {
        val gradleVersion: Property<String>
        val homeDir: DirectoryProperty
    }

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    private
    var hasCleaned = false

    fun cleanUpCaches() {
        synchronized(this) {
            if (hasCleaned) {
                return
            }
            println("Cleaning up caches...")
            val homeDir = parameters.homeDir.get()

            homeDir.asFile.listFiles()?.filter { it.name.startsWith("distributions-") }?.forEach {
                val workerDir = homeDir.dir(it.name)
                cleanupDistributionCaches(workerDir, GradleVersion.version(parameters.gradleVersion.get()))
            }
            hasCleaned = true
        }
    }

    private
    fun cleanupDistributionCaches(workerDir: Directory, gradleVersion: GradleVersion) {
        // Expire cache snapshots of test Gradle distributions that are older than the tested version
        // Also expire version-specific cache snapshots when they can't be re-used (for 'snapshot-1' developer builds)
        val expireDistributionCache = Spec<GradleVersion> { candidateVersion ->
            (candidateVersion.isSnapshot && candidateVersion < gradleVersion)
                || candidateVersion.version.endsWith("-snapshot-1")
        }

        // Remove state for old versions of Gradle that we're unlikely to ever require again
        fileSystemOperations.removeOldVersionsFromDir(workerDir.dir("caches"), expireDistributionCache)

        // Remove scripts caches
        fileSystemOperations.removeCachedScripts(workerDir.dir("caches").asFile)

        // Remove script caches from TestKit integTest temp dir
        // location defined in TempTestKitDirProvider, copied here
        val testKitTmpDir = File(File(System.getProperty("java.io.tmpdir")), String.format(".gradle-test-kit-%s", System.getProperty("user.name")))
        fileSystemOperations.removeCachedScripts(File(testKitTmpDir, "caches"))
        fileSystemOperations.removeTransformDir(File(testKitTmpDir, "caches"))

        fileSystemOperations.removeOldVersionsFromDir(workerDir.dir("daemon"), expireDistributionCache)

        // Remove old distributions used by wrapper that we're unlikely to ever require again
        fileSystemOperations.removeOldVersionsFromDir(workerDir.dir("wrapper/dists"), expireDistributionCache, "gradle-", "-bin")
        fileSystemOperations.delete {
            delete(workerDir.dir("wrapper/dists/dist"))
        }

        // Remove caches that weren't multi-process safe and may be corrupt
        fileSystemOperations.removeDodgyCacheFiles(workerDir.dir("caches"))

        // Remove old daemon log files
        fileSystemOperations.removeDaemonLogFiles(workerDir.dir("daemon"))
    }
}
