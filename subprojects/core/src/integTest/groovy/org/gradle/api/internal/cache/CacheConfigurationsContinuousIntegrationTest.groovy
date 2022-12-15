/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.cache

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.internal.time.TimestampSuppliers


class CacheConfigurationsContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    def "can configure caches via init script and execute multiple builds in a session"() {
        executer.requireOwnGradleUserHomeDir()

        def initDir = new File(executer.gradleUserHomeDir, "init.d")
        initDir.mkdirs()
        new File(initDir, "cache-settings.gradle") << """
            beforeSettings { settings ->
                settings.caches {
                    cleanup = Cleanup.DISABLED
                    releasedWrappers.removeUnusedEntriesAfterDays = 10
                    snapshotWrappers.removeUnusedEntriesAfterDays = 5
                    downloadedResources.removeUnusedEntriesAfterDays = 10
                    createdResources.removeUnusedEntriesAfterDays = 5
                }
            }
        """
        settingsFile << """
            caches {
                releasedWrappers { ${assertValueIsSameInDays(10)} }
                snapshotWrappers { ${assertValueIsSameInDays(5)} }
                downloadedResources { ${assertValueIsSameInDays(10)} }
                createdResources { ${assertValueIsSameInDays(5)} }
            }
        """

        when:
        buildFile << """
            task foo(type: Copy) {
                from 'foo'
                into layout.buildDir.dir('foo')
            }
        """
        file('foo').text = 'bar'

        then:
        succeeds("foo")
        file('build/foo/foo').text == 'bar'

        when:
        file('foo').text = 'baz'

        then:
        buildTriggeredAndSucceeded()
        file('build/foo/foo').text == 'baz'
    }

    def "can change cache configurations in between builds in a session"() {
        executer.requireOwnGradleUserHomeDir()

        def retentionFileName = 'retention'
        def retentionFile = file(retentionFileName)
        def initDir = new File(executer.gradleUserHomeDir, "init.d")
        initDir.mkdirs()
        new File(initDir, "cache-settings.gradle") << """
            beforeSettings { settings ->
                def retentionFile = new File(settings.rootDir, '${retentionFileName}')
                def retentionFileProperty = settings.services.get(ObjectFactory).fileProperty().fileValue(retentionFile)
                def retentionTimeStampProvider = settings.providers.fileContents(retentionFileProperty).asText.map { it as long }
                settings.caches {
                    cleanup = Cleanup.DISABLED
                    releasedWrappers.removeUnusedEntriesOlderThan = retentionTimeStampProvider
                    snapshotWrappers.removeUnusedEntriesOlderThan = retentionTimeStampProvider
                    downloadedResources.removeUnusedEntriesOlderThan = retentionTimeStampProvider
                    createdResources.removeUnusedEntriesOlderThan = retentionTimeStampProvider
                }
            }
        """
        buildFile << """
            def retentionFile = file('${retentionFileName}')
            task foo(type: Copy) {
                from '${retentionFileName}'
                into layout.buildDir.dir('foo')
                doLast {
                    def retentionTimestamp = retentionFile.text as long
                    println "retention timestamp is " + retentionTimestamp
                    def caches = services.get(CacheConfigurations)
                    caches.with {
                        releasedWrappers { assert removeUnusedEntriesOlderThan.get() == retentionTimestamp }
                        snapshotWrappers { assert removeUnusedEntriesOlderThan.get() == retentionTimestamp }
                        downloadedResources { assert removeUnusedEntriesOlderThan.get() == retentionTimestamp}
                        createdResources { assert removeUnusedEntriesOlderThan.get() == retentionTimestamp }
                    }
                }
            }
        """

        when:
        retentionFile.text = TimestampSuppliers.daysAgo(10).get()
        succeeds("foo")

        then:
        outputContains("retention timestamp is " + retentionFile.text)

        when:
        retentionFile.text = TimestampSuppliers.daysAgo(20).get()

        then:
        buildTriggeredAndSucceeded()
        outputContains("retention timestamp is " + retentionFile.text)

        when:
        retentionFile.text = TimestampSuppliers.daysAgo(15).get()

        then:
        buildTriggeredAndSucceeded()
        outputContains("retention timestamp is " + retentionFile.text)
    }

    static String assertValueIsSameInDays(configuredDaysAgo) {
        return """
            def timestamp = removeUnusedEntriesOlderThan.get()
            def daysAgo = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - timestamp)
            assert daysAgo == ${configuredDaysAgo}
        """
    }
}
