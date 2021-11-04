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
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.test.fixtures.file.TestFile

class DispatchingBuildCacheIntegrationTest extends AbstractIntegrationSpec {

    private TestBuildCache localCache = new TestBuildCache(file('local-cache'))
    private TestBuildCache remoteCache = new TestBuildCache(file('remote-cache'))
    private TestFile inputFile = file('input.txt')
    private TestFile cacheOriginInputFile = file('cache-origin.txt')
    private TestFile outputFile = file('build/output.txt')
    private String cacheableTask = ':cacheableTask'

    def setup() {
        inputFile.text = 'This is the input'
        cacheOriginInputFile.text = 'And this is not'

        buildScript """           
            apply plugin: 'base'

            import org.gradle.api.*
            import org.gradle.api.tasks.*

            task cacheableTask(type: MyTask) {
                inputFile = file('input.txt')
                outputFile = file('build/output.txt')
                cacheOrigin = file('cache-origin.txt')
            }
            
            @CacheableTask
            class MyTask extends DefaultTask {

                @OutputFile File outputFile
                
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile
                @Internal
                File cacheOrigin

                @TaskAction void doSomething() {
                    outputFile.text = inputFile.text + cacheOrigin.text
                }
            }
        """.stripIndent()
    }

    def 'push to local'() {
        pushToLocal()

        when:
        withBuildCache().run cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        populatedCache(localCache)
        remoteCache.empty

        when:
        pullOnly()
        withBuildCache().run 'clean', cacheableTask

        then:
        skipped(cacheableTask)
        populatedCache(localCache)
        remoteCache.empty

    }

    def 'push to remote'() {
        pushToRemote()

        when:
        withBuildCache().run cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        populatedCache(remoteCache)
        localCache.empty

        when:
        pullOnly()
        withBuildCache().run 'clean', cacheableTask

        then:
        skipped(cacheableTask)
        populatedCache(remoteCache)
        localCache.empty

    }

    def 'pull from local first'() {
        pushToRemote()
        cacheOriginInputFile.text = 'remote'

        when:
        withBuildCache().run cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        populatedCache(remoteCache)
        localCache.empty

        when:
        settingsFile.text = localCache.localCacheConfiguration()
        cacheOriginInputFile.text = 'local'
        withBuildCache().run 'clean', cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        populatedCache(localCache)
        populatedCache(remoteCache)

        when:
        pullOnly()
        cacheOriginInputFile.text = 'remote'
        withBuildCache().run 'clean', cacheableTask

        then:
        populatedCache(localCache)
        populatedCache(remoteCache)
        outputFile.text == inputFile.text + 'local'
    }

    def 'push to the local cache by default'() {
        settingsFile.text = """
            buildCache {        
                local {
                    directory = '${localCache.cacheDir.toURI()}'                    
                }
                remote(DirectoryBuildCache) {
                    directory = '${remoteCache.cacheDir.toURI()}'
                }
            }            
        """.stripIndent()

        when:
        withBuildCache().run cacheableTask

        then:
        populatedCache(localCache)
        remoteCache.empty
    }

    def 'push to local and remote'() {
        pushToBoth()

        when:
        withBuildCache().run cacheableTask

        then:
        populatedCache(localCache)
        populatedCache(remoteCache)
        def localCacheFile = localCache.listCacheFiles().first()
        def remoteCacheFile = remoteCache.listCacheFiles().first()
        localCacheFile.md5Hash == remoteCacheFile.md5Hash
        localCacheFile.name == remoteCacheFile.name
    }

    void pulledFrom(TestBuildCache cache) {
        skipped(cacheableTask)
        assert cache.listCacheFiles().size() == 1
    }

    private static boolean populatedCache(TestBuildCache cache) {
        cache.listCacheFiles().size() == 1
    }

    void pushToRemote() {
        setupCacheConfiguration(false, true)
    }

    void pushToLocal() {
        setupCacheConfiguration(true, false)
    }

    void pullOnly() {
        setupCacheConfiguration(false, false)
    }

    void pushToBoth() {
        setupCacheConfiguration(true, true)
    }

    private void setupCacheConfiguration(boolean pushToLocal, boolean pushToRemote) {
        settingsFile.text = localCache.localCacheConfiguration(pushToLocal) + remoteCache.remoteCacheConfiguration(pushToRemote)
    }

}
