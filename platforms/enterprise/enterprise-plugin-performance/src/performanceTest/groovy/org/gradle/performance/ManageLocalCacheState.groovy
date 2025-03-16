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

package org.gradle.performance

import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.test.fixtures.file.TestFile

class ManageLocalCacheState implements BuildMutator {
    final File projectDir

    ManageLocalCacheState(File projectDir) {
        this.projectDir = projectDir
    }

    @Override
    void beforeBuild(BuildContext context) {
        def projectTestDir = new TestFile(projectDir)
        def cacheDir = projectTestDir.file('local-build-cache')
        def settingsFile = projectTestDir.file('settings.gradle')
        settingsFile << """
                buildCache {
                    local {
                        directory = '${cacheDir.absoluteFile.toURI()}'
                    }
                }
            """.stripIndent()
    }

    @Override
    void afterBuild(BuildContext context, Throwable t) {
        assert !new File(projectDir, 'error.log').exists()
        def buildCacheDirectory = new TestFile(projectDir, 'local-build-cache')
        def cacheEntries = buildCacheDirectory.listFiles()
        if (cacheEntries == null) {
            throw new IllegalStateException("Cache dir doesn't exist, did the build succeed? Please check the build log.")
        }

        cacheEntries.sort().eachWithIndex { TestFile entry, int i ->
            if (i % 2 == 0) {
                entry.delete()
            }
        }
    }
}
