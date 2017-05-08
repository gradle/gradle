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
import org.gradle.test.fixtures.server.http.HttpBuildCache
import org.junit.Rule
import spock.lang.Unroll

class FinalizeBuildCacheConfigurationBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    private static final SOME_CREDENTIALS =
        """
            remote.credentials {
                username = "user"
                password = "pass"
            }
        """

    private static final NO_CREDENTIALS = ''

    private static final INCOMPLETE_CREDENTIALS =
        """
            remote.credentials {
                username = "user"
            }
        """

    @Rule
    BuildOperationsFixture buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @Rule
    HttpBuildCache httpBuildCache = new HttpBuildCache(testDirectoryProvider)

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
        result.local.className == 'org.gradle.caching.local.DirectoryBuildCache'
        result.local.config.directory == cacheDir.absoluteFile.toString()
        result.local.type == 'Directory'
        result.local.push == true

        result.remote == null
    }

    @Unroll
    def "remote build cache configuration is exposed"() {
        given:
        httpBuildCache.start()
        def url = "${httpBuildCache.uri}/"
        settingsFile << """
            buildCache {  
                local {
                    enabled = false 
                }
                remote(org.gradle.caching.http.HttpBuildCache) {
                    enabled = true 
                    url = "$url"   
                    push = $push 
                    $credentials
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()

        result.remote.className == 'org.gradle.caching.http.HttpBuildCache'
        result.remote.config.url == url
        result.remote.config.authenticated == authenticated
        result.remote.type == 'HTTP'
        result.remote.push == push

        result.local == null

        where:
        authenticated | credentials            | push
        'true'        | SOME_CREDENTIALS       | true
        'false'       | NO_CREDENTIALS         | false
        'false'       | INCOMPLETE_CREDENTIALS | false
    }

    def "remote build cache configuration is exposed when basic auth is encoded on the url"() {
        given:
        httpBuildCache.start()
        def safeUri = httpBuildCache.uri
        def basicAuthUri = new URI(safeUri.getScheme(), 'user@pwd', safeUri.getHost(), safeUri.getPort(), safeUri.getPath(), safeUri.getQuery(), safeUri.getFragment())
        settingsFile << """
            buildCache {  
                remote(org.gradle.caching.http.HttpBuildCache) {
                    enabled = true 
                    url = "${basicAuthUri}/"   
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def config = result().remote.config
        config.url == safeUri.toString() + '/'
        config.authenticated == 'true'
    }

    def "custom build cache connector configuration is exposed"() {
        given:
        def type = 'CustomBuildCache Desc'
        def directory = 'someLocation'
        settingsFile << """
            class VisibleNoOpBuildCacheService implements BuildCacheService {
                @Override boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException { false }
                @Override void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {}
                @Override String getDescription() { "NO-OP build cache" }
                @Override void close() throws IOException {}
            }
            class CustomBuildCache extends AbstractBuildCache {}
            class CustomBuildCacheFactory implements BuildCacheServiceFactory<CustomBuildCache> {
                @Override BuildCacheService createBuildCacheService(CustomBuildCache configuration, BuildCacheDescriber describer) { 
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
        result.local.className == 'CustomBuildCache'
        result.local.config.directory == directory
        result.local.type == type

    }

    def "null build cache configurations are exposed when build cache is not enabled"() {
        when:
        succeeds("help")

        then:
        def result = result()
        result.local == null
        result.remote == null
    }

    Map<String, ?> result() {
        buildOperations.result("Finalize build cache configuration")
    }

}
