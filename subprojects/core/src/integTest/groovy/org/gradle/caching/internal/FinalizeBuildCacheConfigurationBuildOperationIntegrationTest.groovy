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

import org.gradle.caching.local.DirectoryBuildCache
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.junit.Rule

class FinalizeBuildCacheConfigurationBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BuildOperationsFixture buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "local build cache configuration is exposed"() {
        given:
        def cacheDir = temporaryFolder.file("cache-dir").createDir()
        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
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
        result.enabled == true

        result.local.enabled == true
        result.local.className == 'org.gradle.caching.local.DirectoryBuildCache'
        result.local.config.location == cacheDir.absoluteFile.toString()
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
                
                local(CustomBuildCache)
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()
        result.enabled == true

        result.local.enabled == true
        result.local.className == 'CustomBuildCache'
        result.local.config.directory == directory
        result.local.type == type

    }

    def "build cache configurations are exposed when build cache is not enabled"() {
        when:
        def cacheDir = temporaryFolder.file("cache-dir").createDir()
        httpBuildCache.start()
        def url = "${httpBuildCache.uri}/"
        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
                    enabled = true 
                    directory = '${cacheDir.absoluteFile.toURI().toString()}'
                }
                remote(org.gradle.caching.http.HttpBuildCache) {
                    enabled = true 
                    url = "$url"   
                }
            }
        """
        succeeds("help")

        then:
        def result = result()
        result.enabled == false

        result.local.enabled == false
        result.local.config.location == cacheDir.absoluteFile.toString()
        result.remote.enabled == false
        result.remote.config.url == url
    }

    def "disabled build cache configurations are exposed"() {
        given:
        def cacheDir = temporaryFolder.file("cache-dir").createDir()
        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
                    enabled = false 
                    directory = '${cacheDir.absoluteFile.toURI().toString()}'
                    push = false 
                }
                remote(DirectoryBuildCache) {
                    enabled = false 
                    directory = '${cacheDir.absoluteFile.toURI().toString()}'   
                    push = false 
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()
        result.enabled == false

        result.local.enabled == false
        result.local.className == DirectoryBuildCache.name
        result.local.config.location == cacheDir.absoluteFile.toString()
        result.local.type == 'directory'
        result.local.push == false

        result.remote.enabled == false
        result.remote.className == DirectoryBuildCache.name
        result.remote.config.location == cacheDir.absoluteFile.toString()
        result.remote.type == 'directory'
        result.remote.push == false
    }

    def "remote build cache configuration is disabled when --offline is provided"() {
        given:
        settingsFile << """
            buildCache {
                remote(DirectoryBuildCache) {
                    enabled = true 
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help", "--offline")

        then:
        def result = result()
        result.enabled == true

        result.remote.enabled == false
        result.remote.className == DirectoryBuildCache.name
    }

    Map<String, ?> result() {
        buildOperations.result("Finalize build cache configuration")
    }

}
