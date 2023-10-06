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

package org.gradle.kotlin.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import spock.lang.Ignore

class KotlinScriptCacheCleanupIntegrationTest
    extends AbstractIntegrationSpec
    implements FileAccessTimeJournalFixture {

    @Ignore("Needs a fix for parallel artifact transform")
    @UnsupportedWithConfigurationCache(because = "tests script compilation")
    def "cleanup deletes old script cache entries"() {
        given:
        requireOwnGradleUserHomeDir()
        executer.requireIsolatedDaemons()

        and: 'seed script cache to have a baseline to compare against'
        buildKotlinFile.text = """
            tasks.register("run") {
                doLast { println("ok") }
            }
        """
        run 'run'

        and:
        TestFile scriptCacheDir = kotlinDslWorkspace.file('scripts')
        String[] scriptCacheBaseLine = scriptCacheDir.list()

        and:
        TestFile outdatedScriptCache = scriptCacheDir.file('7c8e05b2aa9d61f6b8422a683803c455').tap {
            assert !exists()
            file('classes/Program.class').createFile()
        }
        TestFile gcFile = kotlinDslWorkspace.file('gc.properties')
        gcFile.createFile().lastModified = daysAgo(8)
        writeJournalInceptionTimestamp(daysAgo(8))
        writeLastFileAccessTimeToJournal(outdatedScriptCache, daysAgo(16))

        when:
        run 'run'

        then:
        scriptCacheDir.list() == scriptCacheBaseLine
    }

    private TestFile getKotlinDslWorkspace() {
        userHomeCacheDir.file(GradleVersion.current().version).file('kotlin-dsl')
    }
}
