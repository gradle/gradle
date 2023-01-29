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

package org.gradle.caching


import org.gradle.caching.internal.services.NextGenBuildCacheControllerFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.test.fixtures.server.http.HttpBuildCacheServer
import org.junit.Rule

class NextGenBuildCacheHttpIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    @Rule
    HttpBuildCacheServer httpBuildCacheServer = new HttpBuildCacheServer(temporaryFolder)

    def setup() {
        httpBuildCacheServer.start()
        settingsFile << """
            buildCache {
                remote(HttpBuildCache) {
                    url = "${httpBuildCacheServer.uri}"
                    push = true
                }
            }
        """
    }

    def "compile task is loaded from cache"() {
        buildFile << """
            apply plugin: "java"
        """

        file("src/main/java/Main.java") << """
            public class Main {}
        """

        when:
        runWithCacheNG "compileJava"
        then:
        executedAndNotSkipped ":compileJava"

        def alternativeBuildCache = new TestBuildCache(temporaryFolder.file("cache-dir-2").deleteDir().createDir())
        settingsFile << alternativeBuildCache.localCacheConfiguration()

        when:
        runWithCacheNG "clean", "compileJava"
        then:
        skipped ":compileJava"
    }

    private runWithCacheNG(String... tasks) {
        withBuildCache().run("-D${NextGenBuildCacheControllerFactory.NEXT_GEN_CACHE_SYSTEM_PROPERTY}=true", *tasks)
    }
}
