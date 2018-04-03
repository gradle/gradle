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
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskAction

import org.gradle.util.GradleVersion

import java.io.File


open class CleanUpCaches : DefaultTask() {

    @TaskAction
    fun cleanUpCaches(): Unit = project.run {

        val executingVersion = GradleVersion.version(gradle.gradleVersion)

        // Expire .gradle cache where major version is older than executing version
        val expireTaskCache = Spec<GradleVersion> { candidateVersion ->
            candidateVersion.baseVersion < executingVersion.baseVersion
        }

        // Expire intTestImage cache snapshots that are older than the tested version
        // Also expire version-specific cache snapshots when they can't be re-used (for 'snapshot-1' developer builds)
        val expireIntegTestCache = Spec<GradleVersion> { candidateVersion ->
            (candidateVersion.isSnapshot && candidateVersion < GradleVersion.version(version.toString()))
                || candidateVersion.version.endsWith("-snapshot-1")
        }

        // Remove state for old versions of Gradle that we're unlikely to ever require again
        removeOldVersionsFromDir(file("buildSrc/.gradle"), expireTaskCache)
        removeOldVersionsFromDir(file(".gradle"), expireTaskCache)
        removeOldVersionsFromDir(file("intTestHomeDir/worker-1/caches"), expireIntegTestCache)

        // Remove scripts caches
        removeCachedScripts(file("intTestHomeDir/worker-1/caches"))

        // Remove script caches from TestKit integTest temp dir
        // location defined in TempTestKitDirProvider, copied here
        val testKitTmpDir = File(File(System.getProperty("java.io.tmpdir")), String.format(".gradle-test-kit-%s", System.getProperty("user.name")))
        removeCachedScripts(File(testKitTmpDir, "caches"))

        removeOldVersionsFromDir(file("intTestHomeDir/worker-1/daemon"), expireIntegTestCache)

        // Remove old distributions used by wrapper that we're unlikely to ever require again
        removeOldVersionsFromDir(file("intTestHomeDir/worker-1/wrapper/dists"), expireIntegTestCache, "gradle-", "-bin")
        delete(file("intTestHomeDir/worker-1/wrapper/dists/dist"))

        // Remove caches that weren't multi-process safe and may be corrupt
        removeDodgyCacheFiles(file("intTestHomeDir/worker-1/caches"))

        // Remove old daemon log files
        removeDaemonLogFiles(file("intTestHomeDir/worker-1/daemon"))
    }
}
