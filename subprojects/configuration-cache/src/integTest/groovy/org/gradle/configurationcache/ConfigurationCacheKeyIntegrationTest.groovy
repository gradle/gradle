/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache


import org.gradle.configurationcache.isolated.IsolatedProjectsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import spock.lang.IgnoreIf
import spock.lang.Issue

class ConfigurationCacheKeyIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = newConfigurationCacheFixture()

    def "offline flag is part of the cache key"() {
        when:
        configurationCacheRun "help"
        then:
        configurationCache.assertStateStored()
        countStoredEntries() == 1

        when:
        configurationCacheRun "help", "--offline"
        then:
        configurationCache.assertStateStored()
        countStoredEntries() == 2

        // Now repeat invocations in different order to make sure both entries can be reused
        when:
        configurationCacheRun "help"
        then:
        configurationCache.assertStateLoaded()
        countStoredEntries() == 2

        when:
        configurationCacheRun "help", "--offline"
        then:
        configurationCache.assertStateLoaded()
        countStoredEntries() == 2
    }

    @Issue("https://github.com/gradle/gradle/issues/26049")
    @IgnoreIf({ GradleContextualExecuter.isolatedProjects }) // Isolated Projects option is explicitly controlled by the test
    def "isolated projects flag is part of the cache key"() {
        def isolatedProjects = new IsolatedProjectsFixture(this)

        when:
        configurationCacheRun "help"
        then:
        configurationCache.assertStateStored()
        countStoredEntries() == 1

        when:
        configurationCacheRun "help", "-Dorg.gradle.unsafe.isolated-projects=true"
        then:
        configurationCache.assertStateStored()
        isolatedProjects.assertStateStored {
            projectConfigured(":")
        }
        countStoredEntries() == 2

        // Now repeat invocations in different order to make sure both entries can be reused
        when:
        configurationCacheRun "help"
        then:
        configurationCache.assertStateLoaded()
        countStoredEntries() == 2

        when:
        configurationCacheRun "help", "-Dorg.gradle.unsafe.isolated-projects=true"
        then:
        configurationCache.assertStateLoaded()
        isolatedProjects.assertStateLoaded()
        countStoredEntries() == 2
    }

    private int countStoredEntries() {
        getCacheDir().listFiles().findAll { isCacheEntryDir(it) }.size()
    }

    private boolean isCacheEntryDir(File file) {
        file.isDirectory() && file.list().contains("entry.bin")
    }

    private TestFile getCacheDir() {
        return file(".gradle/configuration-cache")
    }
}
