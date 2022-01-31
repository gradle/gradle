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

package org.gradle.caching

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.integtests.fixtures.executer.ExecutionResult

class BuildCacheLocalCacheIntegrationTest extends AbstractIntegrationSpec {

    def localCache = new TestBuildCache(file("local-cache"))
    def remoteCache = new TestBuildCache(file("remote-cache"))

    void cached() {
        skipped(":t")
    }

    void executed() {
        executed(":t")
    }

    def setup() {
        buildFile << """
            @CacheableTask
            class CustomTask extends DefaultTask {

                @Input
                String val = "foo"

                @Input
                List<String> paths = []

                @OutputDirectory
                File dir = project.file("build/dir")

                @TaskAction
                void generate() {
                    paths.each {
                        def f = new File(dir, it)
                        f.parentFile.mkdirs()
                        f.text = val
                    }
                }
            }

            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """

        settingsFile << localCache.localCacheConfiguration() << remoteCache.remoteCacheConfiguration()

        executer.beforeExecute { it.withBuildCacheEnabled() }
    }

    def "remote loads are cached locally"() {
        given:
        settingsFile << """
            buildCache { local.push = false }
        """

        when:
        succeeds("t")

        then:
        executed()
        localCache.empty
        remoteCache.listCacheFiles().size() == 1

        when:
        settingsFile << """
            buildCache.local.push = true
        """
        execute()

        then:
        cached()
        localCache.listCacheFiles().size() == 1

        when:
        settingsFile << """
            buildCache.remote.enabled = false
        """
        assert remoteCache.cacheDir.deleteDir()
        execute()

        then:
        cached()
    }

    def "remote loads are not cached locally if local cache is #state"() {
        given:
        settingsFile << """
            buildCache { local.push = false }
        """

        when:
        execute()

        then:
        executed()
        localCache.empty
        remoteCache.listCacheFiles().size() == 1

        when:
        settingsFile << """
            buildCache {
                $localCacheConfig
            }
        """
        execute()

        then:
        cached()
        localCache.empty

        when:
        assert remoteCache.cacheDir.deleteDir()
        settingsFile << """
            buildCache.remote.enabled = false
            buildCache.local.enabled = true
            buildCache.local.push = false
        """
        execute()

        then:
        executed()

        where:
        localCacheConfig        | state
        "local.enabled = false" | "disabled"
        "local.push = false"    | "pull only"
    }

    ExecutionResult execute() {
        succeeds("clean", "t")
    }

}
