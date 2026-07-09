/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl.fixtures

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheProblemsExecutionResultFixture
import org.gradle.test.fixtures.file.TestFile

abstract class AbstractConfigurationCacheOptInFeatureIntegrationTest extends AbstractIntegrationSpec {
    static final String WARN_PROBLEMS_CLI_OPT = "--${StartParameterBuildOptions.ConfigurationCacheProblemsOption.LONG_OPTION}=warn"

    static final String DISABLE_CC_RECOVERY = "-Dorg.gradle.configuration-cache.recover-from-cache-corruption=false"

    protected ConfigurationCacheProblemsExecutionResultFixture problems

    def setup() {
        // Verify that the previous test cleaned up state correctly
        assert System.getProperty(StartParameterBuildOptions.ConfigurationCacheOption.PROPERTY_NAME) == null
        assert System.getProperty(StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME) == null
        problems = new ConfigurationCacheProblemsExecutionResultFixture(testDirectory)
    }

    def cleanup() {
        // Verify that the test (or fixtures) has cleaned up state correctly
        assert System.getProperty(StartParameterBuildOptions.ConfigurationCacheOption.PROPERTY_NAME) == null
        assert System.getProperty(StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME) == null
    }

    /**
     * The single configuration cache entry directory (the one containing {@code entry.bin}) under the given cache root.
     */
    protected static TestFile cacheEntryDir(TestFile cacheDir) {
        cacheDir.listFiles().findAll { it.directory && it.file("entry.bin").exists() }.with {
            assert size() == 1
            first()
        }
    }

    protected static void corrupt(TestFile file) {
        assert file.exists()
        file.bytes = "corrupt".bytes
    }

    /**
     * Corrupts the stored work graph while keeping {@code entry.bin} and the fingerprints valid, so the entry is still
     * selected and loaded (exercising the load-time recovery path rather than the fingerprint check).
     */
    protected static void corruptWorkState(TestFile cacheDir) {
        def keep = ['entry.bin', 'buildfingerprint.bin', 'projectfingerprint.bin', 'classloaderscopes.bin']
        def stateFiles = cacheEntryDir(cacheDir).listFiles().findAll { it.name.endsWith(".bin") && it.name !in keep }
        assert !stateFiles.empty
        stateFiles.each { corrupt(it) }
    }
}
