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

import org.gradle.test.fixtures.file.TestFile

import static org.gradle.util.internal.CollectionUtils.single

class ConfigurationCacheMultiEntriesPerKeyIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = newConfigurationCacheFixture()

    def 'stores single entry per key by default'() {
        given:
        settingsFile.text = '// original branch'

        when:
        configurationCacheRun 'help'

        then:
        assertStateStored()

        when:
        settingsFile.text = '// second branch'

        and:
        configurationCacheRun 'help'

        then:
        assertStateStored()

        when: 'switching back to original branch'
        settingsFile.text = '// original branch'

        then:
        assertStateStored()

        and:
        configurationCacheEntryDirs.size() == 1
    }

    def 'can store multiple entries per key'() {
        given:
        withMaxEntriesPerKey 2

        and:
        settingsFile.text = '// original branch'

        when:
        configurationCacheRun 'help'

        then:
        assertStateStored()

        when:
        settingsFile.text = '// second branch'

        and:
        configurationCacheRun 'help'

        then:
        assertStateStored()

        when: 'switching back to original branch'
        settingsFile.text = '// original branch'

        and:
        configurationCacheRun 'help'

        then:
        assertStateLoaded()

        when:
        settingsFile.text = '// second branch'

        and:
        configurationCacheRun 'help'

        then:
        assertStateLoaded()

        when:
        settingsFile.text = '// third branch'

        and:
        configurationCacheRun 'help'

        then:
        assertStateStored()

        when: 'switching back to original branch'
        settingsFile.text = '// original branch'

        and:
        configurationCacheRun 'help'

        then: 'least recently used entries are evicted'
        assertStateStored()

        and:
        configurationCacheEntryDirs.size() == 2
    }

    def 'evicted entry is collected'() {
        given:
        withMaxEntriesPerKey 1

        and:
        settingsFile.text = '// original branch'

        and:
        configurationCacheRun 'help'

        when:
        settingsFile.text = '// second branch'

        and:
        configurationCacheRun 'help'

        then:
        configurationCacheEntryDirs.size() == 1
    }

    def 'evicted entries are collected'() {
        given:
        withMaxEntriesPerKey 3

        when:
        2.times {
            settingsFile.text = "// branch $it"
            configurationCacheRun 'help'
        }

        then:
        def oldEntries = configurationCacheEntryDirs
        oldEntries.size() == 2

        when:
        settingsFile.text = "// branch 3"

        and:
        configurationCacheRun 'help'

        then:
        def allEntries = configurationCacheEntryDirs
        def newest = single(allEntries - oldEntries)

        when:
        withMaxEntriesPerKey 2

        and:
        settingsFile.text == "// branch 4"

        and:
        configurationCacheRun 'help'

        then: "only newly created and newest entries remain"
        def remainingEntries = configurationCacheEntryDirs
        remainingEntries.size() == 2
        remainingEntries.every { it !in oldEntries }
        newest in remainingEntries
    }

    def 'least recently used entry is evicted'() {
        given:
        withMaxEntriesPerKey 2

        when:
        settingsFile.text = '// original branch'
        configurationCacheRun 'help'
        assertStateStored()
        def originalBranch = single(configurationCacheEntryDirs)

        and:
        settingsFile.text = '// second branch'
        configurationCacheRun 'help'
        assertStateStored()
        def secondBranch = single(configurationCacheEntryDirs - originalBranch)

        and:
        settingsFile.text = '// original branch'
        configurationCacheRun 'help'
        assertStateLoaded()

        and:
        settingsFile.text = '// third branch'
        configurationCacheRun 'help'
        assertStateStored()

        then:
        def remaining = configurationCacheEntryDirs
        originalBranch in remaining
        secondBranch !in remaining
        remaining.size() == 2
    }

    private void assertStateLoaded() {
        configurationCache.assertStateLoaded()
    }

    private void assertStateStored() {
        configurationCache.assertStateStored()
    }

    private List<TestFile> getConfigurationCacheEntryDirs() {
        subDirsOf(configurationCacheDir).findAll {
            new File(it, "entry.bin").exists()
        }
    }

    private TestFile getConfigurationCacheDir() {
        file('.gradle/configuration-cache')
    }

    private static List<TestFile> subDirsOf(TestFile dir) {
        dir.listFiles().findAll { it.directory }
    }

    private TestFile withMaxEntriesPerKey(int maxEntriesPerKey) {
        file("gradle.properties") << "org.gradle.configuration-cache.entries-per-key=${maxEntriesPerKey}\n"
    }
}
