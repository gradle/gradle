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

package org.gradle.caching.http.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpBuildCache
import org.junit.Rule

class HttpBuildCacheServiceErrorHandlingIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    HttpBuildCache httpBuildCache = new HttpBuildCache(testDirectoryProvider)

    def setup() {
        buildFile << """   
            import org.gradle.api.*
            apply plugin: 'base'

            @CacheableTask
            class CustomTask extends DefaultTask {
                @Input
                Long fileSize = 1024
            
                @OutputFile
                File outputFile
            
                @TaskAction
                void createFile() {
                    outputFile.withOutputStream { OutputStream out ->
                        def random = new Random()
                        def buffer = new byte[1024]
                        for (def count = 0; count < fileSize; count++) {
                            random.nextBytes(buffer)
                            out.write(buffer)
                        }
                    }
                }
            }
            
            task customTask(type: CustomTask) {
                outputFile = file('build/outputFile.bin')
            }
        """ .stripIndent()
    }


    def "build does not fail if connection drops during store"() {
        httpBuildCache.dropConnectionForPutAfterBytes(1024)
        startServer()

        when:
        executer.withStackTraceChecksDisabled()
        executer.withFullDeprecationStackTraceDisabled()
        withHttpBuildCache().succeeds "customTask"
        then:
        output ==~ /(?s).*org\.gradle\.api\.GradleException: Could not pack property 'outputFile': (Broken pipe|Connection reset).*/
    }

    private void startServer() {
        httpBuildCache.start()
        settingsFile << useHttpBuildCache(httpBuildCache.uri)
    }

    private static String useHttpBuildCache(URI uri) {
        """
            buildCache {  
                local {
                    enabled = false
                }
                remote(org.gradle.caching.http.HttpBuildCache) {
                    url = "${uri}/"   
                    push = true
                }
            }
        """
    }

    def withHttpBuildCache() {
        executer.withBuildCacheEnabled()
        this
    }
}
