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
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

class BuildCacheLocalCacheIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def remoteCacheDir = file("remote-cache")

    void cached() {
        assert ":t" in skippedTasks
    }

    void executed() {
        assert ":t" in executedTasks
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

        settingsFile << """
            buildCache {
                remote(DirectoryBuildCache) {
                    push = true
                    directory = '${TextUtil.escapeString(remoteCacheDir.absolutePath)}' 
                }
            }
        """

        executer.beforeExecute { it.withBuildCacheEnabled() }
    }

    List<TestFile> remoteCacheArtifacts() {
        listCacheFiles(remoteCacheDir)
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
        listCacheFiles().empty
        remoteCacheArtifacts().size() == 1

        when:
        settingsFile << """
            buildCache.local.push = true
        """
        execute()

        then:
        cached()
        listCacheFiles().size() == 1

        when:
        settingsFile << """
            buildCache.remote.enabled = false
        """
        assert remoteCacheDir.deleteDir()
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
        listCacheFiles().empty
        remoteCacheArtifacts().size() == 1

        when:
        settingsFile << """
            buildCache { 
                $localCacheConfig
            }
        """
        execute()

        then:
        cached()
        listCacheFiles().size() == 0

        when:
        assert remoteCacheDir.deleteDir()
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
