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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.LocalBuildCacheFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

@Unroll
class CompositeBuildCacheIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {

    private TestFile localCache
    private TestFile remoteCache
    private String cacheableTask = ':cacheableTask'
    private TestFile inputFile
    private TestFile outputFile

    def 'pulling from local and remote and pushing to #pushTo'(localPush, remotePush, pushTo) {
        setupProject(localPush, remotePush)
        TestFile pushPullCache = localPush ? localCache : remoteCache
        TestFile pullOnlyCache = localPush ? remoteCache : localCache

        when:
        withBuildCache().succeeds cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        cachedFiles(pushPullCache).size() == 1
        emptyCache(pullOnlyCache)

        when:
        withBuildCache().succeeds 'clean', cacheableTask

        then:
        pulledFrom(pushPullCache)
        emptyCache(pullOnlyCache)

        when:
        cachedFiles(pushPullCache)[0].moveToDirectory(pullOnlyCache)
        withBuildCache().succeeds 'clean', cacheableTask

        then:
        pulledFrom(pullOnlyCache)
        emptyCache(pushPullCache)

        where:
        localPush | remotePush
        true      | false
        false     | true

        pushTo = localPush ? 'local' : 'remote'
    }

    def 'pull from local and remote'() {
        setupProject(true, false)
        when:
        withBuildCache().succeeds cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        cachedFiles(localCache).size() == 1
        emptyCache(remoteCache)

        when:
        setupProject(false, false)
        withBuildCache().succeeds 'clean', cacheableTask

        then:
        pulledFrom(localCache)
        emptyCache(remoteCache)

        when:
        cachedFiles(localCache)[0].moveToDirectory(remoteCache)
        withBuildCache().succeeds 'clean', cacheableTask

        then:
        pulledFrom(remoteCache)
        emptyCache(localCache)
    }

    def 'pull from local first'() {
        setupProject(false, true)
        inputFile.text = 'remote cache'

        when:
        withBuildCache().succeeds cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        cachedFiles(remoteCache).size() == 1
        emptyCache(localCache)

        when:
        setupProject(true, false)
        inputFile.text = 'local cache'
        withBuildCache().succeeds cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        cachedFiles(localCache).size() == 1
        cachedFiles(remoteCache).size() == 1

        when:
        inputFile.text = 'remote cache'
        def cacheKey = cachedFiles(remoteCache)[0].name
        cachedFiles(localCache)[0].renameTo(localCache.file(cacheKey))
        withBuildCache().succeeds 'clean', cacheableTask

        then:
        pulledFrom(localCache)
        outputFile.text == 'local cache'
    }

    def 'push is disabled to the remote cache by default'() {
        setupProject(false, false)
        settingsFile.text = """
            buildCache {        
                local {
                    directory = '${localCache.absoluteFile.toURI()}'                    
                }
                remote(LocalBuildCache) {
                    directory = '${remoteCache.absoluteFile.toURI()}'
                }
            }            
        """.stripIndent()

        when:
        withBuildCache().succeeds cacheableTask

        then:
        cachedFiles(localCache).size() == 1
        cachedFiles(remoteCache).empty
    }

    def 'configuring pushing to remote and local yields a reasonable error'() {
        setupProject(true, true)

        when:
        withBuildCache().fails cacheableTask

        then:
        failure.assertHasCause('It is only allowed to push to a remote or a local build cache, not to both. Disable push for one of the caches')
    }

    void pulledFrom(cacheDir) {
        assert skippedTasks.contains(cacheableTask)
        assert cachedFiles(cacheDir).size() == 1
    }

    void emptyCache(cacheDir) {
        assert cachedFiles(cacheDir).empty
    }

    private void setupProject(pushToLocal = true, pushToRemote = false) {
        localCache = file('local-cache')
        remoteCache = file('remote-cache')
        cacheableTask = ':cacheableTask'
        inputFile = file('input.txt')
        outputFile = file('build/output.txt')

        settingsFile.text = """
            buildCache {
                local {
                    directory = '${localCache.absoluteFile.toURI()}' 
                    push = ${pushToLocal}
                }
                remote(LocalBuildCache) {
                    directory = '${remoteCache.absoluteFile.toURI()}'
                    push = ${pushToRemote}
                }
            }
        """.stripIndent()

        buildScript """           
            apply plugin: 'base'

            task cacheableTask {
                ext.output = file('build/output.txt')
                outputs.cacheIf { true }
                inputs.file('input.txt').withPathSensitivity(PathSensitivity.NONE)
                outputs.file(output).withPropertyName('output')
                
                doLast {
                    output.parentFile.mkdirs()
                    output.text = file('input.txt').text 
                }
            }
        """.stripIndent()

        inputFile.text = 'This is the input'
    }

    private static List<TestFile> cachedFiles(TestFile cacheDir) {
        def cacheEntryNames = cacheDir.allDescendants().findAll { !it.endsWith('.lock') } as List<String>
        cacheDir.files(*cacheEntryNames)
    }
}
