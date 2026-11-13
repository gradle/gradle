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

package org.gradle.internal.cc.impl

import org.gradle.test.fixtures.file.TestFile

/**
 * Captures how a build behaves when the state files of an otherwise valid cache entry are damaged.
 */
class ConfigurationCacheCorruptEntryIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    private static final byte[] CORRUPT_MARKER = "corrupt".bytes
    private static final String TRUNCATED_STREAM = "reached end of stream after reading 7 bytes; 16 bytes expected"

    def "fails without running tasks when the work state is corrupted"() {
        given:
        withHelloTask()
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("hello")

        then:
        configurationCache.assertStateStored()
        outputContains("Hello")

        when:
        corruptWorkState()
        executer.withStackTraceChecksDisabled()
        configurationCacheFails("hello")

        then:
        outputContains("Reusing configuration cache.")
        // TODO This failure should name the configuration cache as the cause, not just the read error.
        failure.assertHasDescription(TRUNCATED_STREAM)

        and:
        outputDoesNotContain("Hello")
        hasCorruptedState()
    }

    def "fails without running tasks when #what is corrupted"() {
        given:
        withHelloTask()
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("hello")

        then:
        configurationCache.assertStateStored()
        outputContains("Hello")

        when:
        corrupt(this)
        executer.withStackTraceChecksDisabled()
        configurationCacheFails("hello")

        then:
        // TODO This failure should name the configuration cache as the cause, not just the read error.
        failure.assertHasDescription(expectedMessage)

        and:
        outputDoesNotContain("Hello")
        hasCorruptedState()

        where:
        what          | corrupt                     | expectedMessage
        "metadata"    | { it.corruptMetadata() }    | "Index 99 out of bounds for length 0"
        "fingerprint" | { it.corruptFingerprint() } | TRUNCATED_STREAM
    }

    private void withHelloTask() {
        buildFile """
            tasks.register("hello") {
                doLast { println "Hello" }
            }
        """
    }

    /**
     * Damages every file the entry is loaded from, leaving the files the fingerprint check
     * reads (the metadata, the classloader scopes and the fingerprints) intact, so that the
     * entry is still considered reusable.
     */
    private void corruptWorkState() {
        def readByCheck = ["entry.bin", "buildfingerprint.bin", "projectfingerprint.bin", "classloaderscopes.bin"]
        def stateFiles = cacheEntryDir().listFiles().findAll {
            it.name.endsWith(".bin") && !(it.name in readByCheck)
        }
        assert !stateFiles.empty
        stateFiles.each { overwriteWithGarbage(it) }
    }

    protected void corruptMetadata() {
        overwriteWithGarbage(cacheEntryDir().file("entry.bin"))
    }

    protected void corruptFingerprint() {
        overwriteWithGarbage(cacheEntryDir().file("projectfingerprint.bin"))
    }

    private TestFile cacheEntryDir() {
        configurationCacheDir.listFiles().findAll { it.directory && it.file("entry.bin").exists() }.with {
            assert size() == 1
            first()
        }
    }

    private static void overwriteWithGarbage(TestFile file) {
        assert file.exists()
        file.bytes = CORRUPT_MARKER
    }

    private boolean hasCorruptedState() {
        def corrupted = []
        configurationCacheDir.eachFileRecurse { file ->
            if (file.file && Arrays.equals(file.bytes, CORRUPT_MARKER)) {
                corrupted << file
            }
        }
        return !corrupted.empty
    }
}
