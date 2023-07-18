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

import org.eclipse.jetty.server.Response
import org.gradle.caching.internal.services.BuildCacheControllerFactory

import java.util.concurrent.atomic.AtomicInteger

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

class HttpBuildCacheServiceErrorHandlingIntegrationTest extends HttpBuildCacheFixture {
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

            def customTask = tasks.register("customTask", CustomTask) {
                outputFile = file('build/outputFile.bin')
            }

            tasks.register("customTask2", CustomTask) {
                mustRunAfter(customTask)
                outputFile = file('build/outputFile2.bin')
            }
        """.stripIndent()
    }

    def "build does not fail if connection drops during store and server dies"() {
        // Drop the connection and stop the server after reading 1024 bytes
        httpBuildCacheServer.addResponder { req, res ->
            if (req.method == "PUT") {
                1024.times { req.inputStream.read() }
                httpBuildCacheServer.stop()
                false
            } else {
                true
            }
        }
        settingsFile << withHttpBuildCacheServer()

        // We see connection refused because the first partial request is retried,
        // then the subsequent is flat refused because we stopped the server.
        String errorPattern = /(Connect to 127\.0\.0\.1:\d+ \[\/127\.0\.0\.1\] failed: Connection refused|127\.0\.0\.1:\d+ failed to respond|Connection reset)/

        when:
        executer.withStackTraceChecksDisabled()
        withBuildCache().run "customTask"

        then:
        output =~ /Could not store entry .* in remote build cache: ${errorPattern}/
    }

    def "build does not fail if connection repeatedly drops during store"() {
        // For every write, read some of the data and then close the connection
        httpBuildCacheServer.addResponder { req, res ->
            if (req.method == "PUT") {
                1024.times { req.inputStream.read() }
                (res as Response).httpChannel.connection.close()
                false
            } else {
                true
            }
        }
        settingsFile << withHttpBuildCacheServer()
        String errorPattern = /(Broken pipe|Connection reset|Software caused connection abort: socket write error|An established connection was aborted by the software in your host machine|127.0.0.1:.+ failed to respond)/

        when:
        executer.withStackTraceChecksDisabled()
        withBuildCache().run "customTask"

        then:
        output =~ /Could not store entry .* in remote build cache: ${errorPattern}/
    }

    def "dropped connection during write is retried and can subsequently succeed"() {
        // For the first write, read some of the data and then close the connection.
        // Subsequent writes succeed.
        def count = 0
        httpBuildCacheServer.addResponder { req, res ->
            if (req.method == "PUT" && count++ > 0) {
                1024.times { req.inputStream.read() }
                (res as Response).httpChannel.connection.close()
                false
            } else {
                true
            }
        }
        settingsFile << withHttpBuildCacheServer()

        when:
        withBuildCache().run "customTask"

        then:
        count == 1
        httpBuildCacheServer.cacheDir.listFiles().size() == 1
    }

    def "transient error on read is retried"() {
        given:
        settingsFile << withHttpBuildCacheServer()
        withBuildCache().run "customTask"
        withBuildCache().run "clean"

        when:
        def requests = 0
        httpBuildCacheServer.addResponder { req, res ->
            if (requests++ == 0) {
                (res as Response).httpChannel.connection.close()
                false
            } else {
                true
            }
        }
        withBuildCache().run "customTask"

        then:
        skipped ":customTask"

        and:
        requests == 2
    }

    def "build cache is deactivated for the build if the connection times out"() {
        httpBuildCacheServer.blockIncomingConnectionsForSeconds = 10
        settingsFile << withHttpBuildCacheServer()

        when:
        executer.withArgument("-D${SOCKET_TIMEOUT_SYSTEM_PROPERTY}=1000")
        withBuildCache().run("customTask")

        then:
        output =~ /Could not load entry .* from remote build cache: Read timed out/
    }

    def "build cache is deactivated if response is not successful"() {
        def requestCounter = new AtomicInteger()
        httpBuildCacheServer.addResponder { req, res ->
            requestCounter.incrementAndGet()
            res.sendError(500)
            false
        }
        settingsFile << withHttpBuildCacheServer()

        when:
        withBuildCache().run("customTask", "customTask2")

        then:
        output =~ /Could not load entry .* from remote build cache: Loading entry from '.+' response status 500: Server Error/

        and:
        requestCounter.get() == 1
    }

    def "build cache is not deactivated if response is not successful and continue-on-error is disabled"() {
        def requestCounter = new AtomicInteger()
        httpBuildCacheServer.addResponder { req, res ->
            requestCounter.incrementAndGet()
            res.sendError(500)
            false
        }
        settingsFile << withHttpBuildCacheServer()

        when:
        withBuildCache().run("-D${BuildCacheControllerFactory.REMOTE_CONTINUE_ON_ERROR_PROPERTY}=true", "customTask", "customTask2")

        then:
        output =~ /Could not load entry .* from remote build cache: Loading entry from '.+' response status 500: Server Error/

        and:
        requestCounter.get() == 4 // {MISS,STORE} * 2
    }
}
