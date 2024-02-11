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

package org.gradle.caching.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class FinalizeBuildCacheConfigurationBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "local build cache configuration is exposed"() {
        given:
        def cacheDir = temporaryFolder.file("cache-dir").createDir()
        settingsFile << """
            buildCache {
                local {
                    enabled = true
                    directory = '${cacheDir.absoluteFile.toURI().toString()}'
                    push = true
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()

        result.enabled
        result.localEnabled
        !result.remoteEnabled

        result.local.className == 'org.gradle.caching.local.DirectoryBuildCache'
        result.local.config.location == cacheDir.absoluteFile.toString()
        result.local.config.removeUnusedEntriesAfter == "7 days"
        result.local.type == 'directory'
        result.local.push == true

        result.remote == null
    }

    def "custom build cache connector configuration is exposed"() {
        given:
        def type = 'CustomBuildCache Desc'
        def directory = 'someLocation'
        settingsFile << """
            class VisibleNoOpBuildCacheService implements BuildCacheService {
                @Override boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException { false }
                @Override void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {}
                @Override void close() throws IOException {}
            }
            class CustomBuildCache extends AbstractBuildCache {}
            class CustomBuildCacheFactory implements BuildCacheServiceFactory<CustomBuildCache> {
                @Override BuildCacheService createBuildCacheService(CustomBuildCache configuration, Describer describer) {
                    describer.type('$type').config('directory', '$directory')
                    new VisibleNoOpBuildCacheService()
                }
            }

            buildCache {
                registerBuildCacheService(CustomBuildCache, CustomBuildCacheFactory)

                remote(CustomBuildCache)
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()

        result.enabled
        result.remoteEnabled

        result.remote.className == 'CustomBuildCache'
        result.remote.config.directory == directory
        result.remote.type == type

    }

    def "build cache configurations are not exposed when build cache is not enabled"() {
        when:
        def cacheDir = temporaryFolder.file("cache-dir").createDir()
        def url = "http://localhost:8080/cache/"
        settingsFile << """
            class NoOpBuildCacheService implements BuildCacheService {
                @Override
                boolean load(BuildCacheKey key, BuildCacheEntryReader reader) { false }

                @Override
                void store(BuildCacheKey key, BuildCacheEntryWriter writer) {}

                @Override
                void close() {}
            }

            class CustomBuildCache extends AbstractBuildCache {
                private URI url
                URI getUrl() {}
                void setUrl(String url) { this.url = URI.create(url) }
            }

            class CustomBuildCacheFactory implements BuildCacheServiceFactory<CustomBuildCache> {
                @Override BuildCacheService createBuildCacheService(CustomBuildCache configuration, Describer describer) {
                    describer.config('url', '$url')
                    new NoOpBuildCacheService()
                }
            }
            buildCache {
                registerBuildCacheService(CustomBuildCache, CustomBuildCacheFactory)

                local {
                    enabled = true
                    directory = '${cacheDir.absoluteFile.toURI().toString()}'
                }
                remote(CustomBuildCache) {
                    enabled = true
                    url = "$url"
                }
            }
        """
        succeeds("help")

        then:
        def result = result()

        !result.enabled
        !result.localEnabled
        !result.remoteEnabled

        result.local == null
        result.remote == null
    }

    def "local build cache configuration is not exposed when disabled"() {
        given:
        def cacheDir = temporaryFolder.file("cache-dir").createDir()
        settingsFile << """
            buildCache {
                local {
                    enabled = false
                    directory = '${cacheDir.absoluteFile.toURI().toString()}'
                    push = true
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()

        result.enabled
        !result.localEnabled

        result.local == null
    }

    Map<String, ?> result() {
        buildOperations.result("Finalize build cache configuration")
    }

}
