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

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

import static org.junit.Assume.assumeFalse

@Issue("https://github.com/gradle/gradle/issues/26663")
class ConfigurationCacheCorruptionRecoveryIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    private static final String INTEGRITY_CHECKS = StartParameterBuildOptions.ConfigurationCacheIntegrityCheckOption.PROPERTY_NAME
    private static final String ENABLE_ISOLATED_PROJECTS = "-D${StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME}=true"

    private static final String CORRUPT_ON_LOAD = "The configuration cache entry could not be loaded and has been discarded."
    private static final String CORRUPT_ON_CHECK = "The configuration cache entry could not be checked because it was corrupted and will be discarded."
    private static final String FAILURE_STACK_TRACE = "at org.gradle.internal.cc.impl.DefaultConfigurationCache"
    private static final String ENABLE_INTEGRITY_CHECK = "-D$INTEGRITY_CHECKS=true"
    private static final byte[] CORRUPT_MARKER = "corrupt".bytes
    private static final List<String> ALL_CORRUPTIONS = ["corruptWorkState", "corruptMetadata", "corruptFingerprint", "corruptClassLoaderScopes"]

    def configurationCache = newConfigurationCacheFixture()

    def "recovers from #corruptState (IP: #ipEnabled)"() {
        given:
        assumeClassLoaderScopesAreFingerprinted(ipEnabled, corruptState)
        withIsolatedProjects(ipEnabled)
        buildFile """
            tasks.register("hello") {
                doLast { println "Hello" }
            }
        """

        when:
        configurationCacheRun("hello")

        then:
        configurationCache.assertStateStored()
        outputContains("Hello")

        when:
        "$corruptState"()
        executer.withStackTraceChecksDisabled()
        configurationCacheRun("hello")

        then:
        outputContains(expectedMessage)
        outputContains(FAILURE_STACK_TRACE)
        outputContains("Hello")
        assertNoCorruptedState()

        when:
        configurationCacheRun("hello")

        then:
        configurationCache.assertStateLoaded()
        outputContains("Hello")
        outputDoesNotContain("could not be")

        where:
        corruptState               | expectedMessage
        "corruptWorkState"         | CORRUPT_ON_LOAD
        "corruptMetadata"          | CORRUPT_ON_CHECK
        "corruptFingerprint"       | CORRUPT_ON_CHECK
        "corruptClassLoaderScopes" | CORRUPT_ON_CHECK

        combined:
        ipEnabled << [false, true]
    }

    def "recovers from a corrupted work graph in a build with buildSrc and subprojects (IP: #ipEnabled)"() {
        given:
        withIsolatedProjects(ipEnabled)
        file("buildSrc/src/main/java/Util.java").text = "public class Util {}"
        settingsFile """
            rootProject.name = 'root'
            include 'a', 'b'
        """
        buildFile """
            tasks.register("hello") { doLast { println "Hello from root" } }
        """
        buildFile "a/build.gradle", """tasks.register("hello") { doLast { println "Hello from a" } }"""
        buildFile "b/build.gradle", """tasks.register("hello") { doLast { println "Hello from b" } }"""

        when:
        configurationCacheRun("hello")

        then:
        configurationCache.assertStateStored()
        outputContains("Hello from a")
        outputContains("Hello from b")

        when:
        corruptWorkState()
        executer.withStackTraceChecksDisabled()
        configurationCacheRun("hello")

        then:
        outputContains(CORRUPT_ON_LOAD)
        outputContains("Hello from root")
        outputContains("Hello from a")
        outputContains("Hello from b")
        assertNoCorruptedState()

        when:
        configurationCacheRun("hello")

        then:
        configurationCache.assertStateLoaded()
        outputContains("Hello from a")

        where:
        ipEnabled << [false, true]
    }

    def "fails the build on #corruptState when recovery is disabled (IP: #ipEnabled)"() {
        given:
        assumeClassLoaderScopesAreFingerprinted(ipEnabled, corruptState)
        withIsolatedProjects(ipEnabled)
        buildFile """
            tasks.register("hello") {
                doLast { println "Hello" }
            }
        """

        when:
        configurationCacheRun(DISABLE_CC_RECOVERY, "hello")

        then:
        configurationCache.assertStateStored()

        when:
        "$corruptState"()
        configurationCacheFails(DISABLE_CC_RECOVERY, "hello")

        then:
        outputDoesNotContain(CORRUPT_ON_LOAD)
        outputDoesNotContain(CORRUPT_ON_CHECK)
        outputDoesNotContain("Hello")
        hasCorruptedState()

        where:
        corruptState << ALL_CORRUPTIONS

        combined:
        ipEnabled << [false, true]
    }

    def "reports the original failure when a corrupted entry cannot be recovered (IP: #ipEnabled)"() {
        given:
        withIsolatedProjects(ipEnabled)
        buildFile """
            class BrokenSerializable implements java.io.Serializable {
                private Object readResolve() { throw new RuntimeException("BOOM from readResolve") }
            }
            abstract class BrokenTask extends DefaultTask {
                @Internal final prop = new BrokenSerializable()
                @TaskAction void run() {}
            }
            tasks.register("broken", BrokenTask)
        """

        when:
        configurationCacheFails("broken")

        then:
        failureCauseContains("BOOM from readResolve")

        when:
        executer.withStackTraceChecksDisabled()
        configurationCacheFails("broken")

        then:
        failureDescriptionContains("Could not load the value of field `prop`")
        failureCauseContains("BOOM from readResolve")
        outputDoesNotContain("Cannot create a new id")

        where:
        ipEnabled << [false, true]
    }

    def "the entry stored after recovery is invalidated by a Gradle properties change"() {
        given:
        file("gradle.properties") << "someProperty=first"
        buildFile """
            def someProperty = providers.gradleProperty("someProperty").get()
            tasks.register("hello") {
                doLast { println "someProperty = " + someProperty }
            }
        """

        when:
        configurationCacheRun("hello")

        then:
        configurationCache.assertStateStored()
        outputContains("someProperty = first")

        when:
        corruptWorkState()
        executer.withStackTraceChecksDisabled()
        configurationCacheRun("hello")

        then:
        outputContains(CORRUPT_ON_LOAD)
        outputContains("someProperty = first")

        when:
        file("gradle.properties").text = "someProperty=second"
        configurationCacheRun("hello")

        then:
        configurationCache.assertStateStored()
        outputContains("someProperty = second")
    }

    def "recovers from a corrupted work graph when encryption is enabled"() {
        given:
        def keyStoreOption = "-Dorg.gradle.internal.configuration-cache.key-store-dir=${file("keystores")}"
        buildFile """
            tasks.register("hello") {
                doLast { println "Hello" }
            }
        """

        when:
        configurationCacheRun(keyStoreOption, "hello")

        then:
        configurationCache.assertStateStored()
        outputContains("Hello")

        when:
        corruptWorkState()
        executer.withStackTraceChecksDisabled()
        configurationCacheRun(keyStoreOption, "hello")

        then:
        outputContains(CORRUPT_ON_LOAD)
        outputContains("Hello")
        assertNoCorruptedState()

        when:
        configurationCacheRun(keyStoreOption, "hello")

        then:
        configurationCache.assertStateLoaded()
        outputContains("Hello")
    }

    def "fails the build on #corruptState when integrity check is enabled (IP: #ipEnabled)"() {
        given:
        assumeClassLoaderScopesAreFingerprinted(ipEnabled, corruptState)
        withIsolatedProjects(ipEnabled)
        buildFile """
            tasks.register("hello") {
                doLast { println "Hello" }
            }
        """

        when:
        configurationCacheRun(ENABLE_INTEGRITY_CHECK, "hello")

        then:
        configurationCache.assertStateStored()

        when:
        "$corruptState"()
        configurationCacheFails(ENABLE_INTEGRITY_CHECK, "hello")

        then:
        outputDoesNotContain(CORRUPT_ON_LOAD)
        outputDoesNotContain(CORRUPT_ON_CHECK)
        outputDoesNotContain("Hello")
        hasCorruptedState()

        where:
        corruptState << ALL_CORRUPTIONS

        combined:
        ipEnabled << [false, true]
    }

    private static void assumeClassLoaderScopesAreFingerprinted(boolean ipEnabled, String corruptState) {
        assumeFalse("Isolated Projects does not fingerprint classloader scopes, so it stores no such file",
            ipEnabled && corruptState == "corruptClassLoaderScopes")
    }

    private void withIsolatedProjects(boolean enabled) {
        if (enabled) {
            executer.beforeExecute { it.withArgument(ENABLE_ISOLATED_PROJECTS) }
        }
    }

    private void corruptWorkState() {
        def keep = ['entry.bin', 'buildfingerprint.bin', 'projectfingerprint.bin', 'classloaderscopes.bin']
        def stateFiles = cacheEntryDir().listFiles().findAll { it.name.endsWith(".bin") && it.name !in keep }
        assert !stateFiles.empty
        stateFiles.each { corrupt(it) }
    }

    private void corruptMetadata() {
        corrupt(cacheEntryDir().file("entry.bin"))
    }

    private void corruptFingerprint() {
        corrupt(cacheEntryDir().file("projectfingerprint.bin"))
    }

    private void corruptClassLoaderScopes() {
        corrupt(cacheEntryDir().file("classloaderscopes.bin"))
    }

    private TestFile cacheEntryDir() {
        configurationCacheDir.listFiles().findAll { it.directory && it.file("entry.bin").exists() }.with {
            assert size() == 1
            first()
        }
    }

    private static void corrupt(TestFile file) {
        assert file.exists()
        file.bytes = CORRUPT_MARKER
    }

    private boolean hasCorruptedState() {
        def remaining = []
        configurationCacheDir.eachFileRecurse { file ->
            if (file.file && Arrays.equals(file.bytes, CORRUPT_MARKER)) {
                remaining << file
            }
        }
        return !remaining.empty
    }

    private void assertNoCorruptedState() {
        assert !hasCorruptedState()
    }
}
