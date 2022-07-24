/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.internal.tasks.execution.TaskExecution
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.util.GradleVersion

class CacheTaskOutputIntegrationTest extends AbstractIntegrationSpec {

    def localCache = new TestBuildCache(file("local-cache"))

    def setup() {
        executer.beforeExecute { withBuildCacheEnabled() }
        settingsFile << localCache.localCacheConfiguration()
    }

    def "cached origin metadata is correct"() {
        file("input.txt") << "data"

        buildFile << """
            apply plugin: "java"
        """

        file("src/main/java/Hello.java") << """
            public class Hello {
                public static void main(String... args) {
                    System.out.println("Hello World!");
                }
            }
        """

        when:
        run"compileJava"
        def metadata = readMetadata()

        then:
        metadata.keySet().containsAll(
            "buildInvocationId",
            "creationTime",
            "executionTime",
            "hostName",
        )
        metadata.identity == ":compileJava"
        metadata.type == TaskExecution.name.replaceAll(/\$/, ".")
        metadata.userName == System.getProperty("user.name")
        metadata.operatingSystem == System.getProperty("os.name")
        metadata.gradleVersion == GradleVersion.current().version
    }

    private Properties readMetadata() {
        def cacheFiles = localCache.listCacheFiles()
        assert cacheFiles.size() == 1
        def cacheEntry = cacheFiles[0]

        // Must rename to "*.tgz" for unpacking to work
        def tgzCacheEntry = temporaryFolder.file("cache.tgz")
        cacheEntry.copyTo(tgzCacheEntry)
        def extractDir = temporaryFolder.file("extract")
        tgzCacheEntry.untarTo(extractDir)
        tgzCacheEntry.delete()

        def metadata = new Properties()
        extractDir.file("METADATA").withInputStream { input ->
            metadata.load(input)
        }
        extractDir.deleteDir()
        return metadata
    }
}
