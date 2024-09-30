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

package org.gradle.internal.cc.impl

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class ConfigurationCacheStateTest {
    @JvmField
    @Rule
    val testDirectoryProvider = TestNameTestDirectoryProvider(javaClass)

    @Test
    fun `can obtain a work state file`() {
        val workStateFile = ConfigurationCacheRepository.ReadableLayout(testDirectoryProvider.testDirectory).fileFor(StateType.Work)
        assertStateFileName("work.bin", workStateFile)
    }

    @Test
    fun `can obtain related state file from a work state file`() {
        val workStateFile = ConfigurationCacheRepository.ReadableLayout(testDirectoryProvider.testDirectory).fileFor(StateType.Work)
        assertRelatedStateFileName(workStateFile, "_.work.bin", Path.path(":"))
        assertRelatedStateFileName(workStateFile, "_.work.bin", Path.ROOT)
        assertRelatedStateFileName(workStateFile, "_foo.work.bin", Path.path(":foo"))
        assertRelatedStateFileName(workStateFile, "_foo_bar.work.bin", Path.path(":foo:bar"))
        assertRelatedStateFileName(workStateFile, "foo_bar.work.bin", Path.path("foo:bar"))
    }

    private fun assertRelatedStateFileName(baseStateFile: ConfigurationCacheStateFile, expected: String, relatedPath: Path) {
        assertStateFileName(expected, baseStateFile.relatedStateFile(relatedPath))
    }

    private fun assertStateFileName(expected: String, relatedStateFile: ConfigurationCacheStateFile) {
        Assert.assertEquals(expected, relatedStateFile.stateFile.file.name)
    }
}
