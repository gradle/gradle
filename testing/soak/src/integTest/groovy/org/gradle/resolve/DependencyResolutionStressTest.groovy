/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.resolve

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.NoDaemonGradleExecuter
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.rules.ExternalResource
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DependencyResolutionStressTest extends Specification {
    @Rule TestNameTestDirectoryProvider workspace = new TestNameTestDirectoryProvider(getClass())
    GradleDistribution distribution = new UnderDevelopmentGradleDistribution()
    @Rule StressHttpServer server = new StressHttpServer()
    @Rule ConcurrentTestUtil concurrent = new ConcurrentTestUtil()

    def setup() {
        concurrent.shortTimeout = 180000
    }

    def cleanup() {
        new DaemonLogsAnalyzer(workspace.file("daemon")).daemons.each { it.kill() }
    }

    def "handles concurrent access to changing artifacts"() {
        expect:
        4.times { count ->
            def buildDir = workspace.file(count)
            concurrent.start {
                buildDir.file('build.gradle') << """
import java.util.zip.*

repositories {
    ivy { url = "${server.uri}" }
}

configurations {
    compile
}

dependencies {
    compile('org.gradle:changing:1.0') {
        changing = true
    }
}

task check {
    doLast {
        def file = configurations.compile.singleFile
        println "THREAD $count -> checking \$file.name size: \${file.length()}"
        file.withInputStream { instr ->
            def zipStream = new ZipInputStream(instr)
            def entries = []
            for (ZipEntry entry = zipStream.nextEntry; entry != null; entry = zipStream.nextEntry) {
                entries << entry.name
            }
            assert entries == ['a', 'b']
        }
    }
}
        """

                GradleExecuter executer = new NoDaemonGradleExecuter(distribution, workspace, IntegrationTestBuildContext.INSTANCE).
                        requireDaemon().requireIsolatedDaemons().
                        withGradleUserHomeDir(workspace.file("user-home"))
                8.times {
                    executer.inDirectory(buildDir).withArgument("--refresh-dependencies").withTasks('check').run()
                }
            }
        }
        concurrent.finished()
    }

    static class StressHttpServer extends ExternalResource {
        private static final String GET_METHOD = 'GET'
        private static final String HEAD_METHOD = 'HEAD'
        private static final String METADATA_FILE_PATH = '/org.gradle/changing/1.0/ivy-1.0.xml'
        private static final String JAR_FILE_PATH = '/org.gradle/changing/1.0/changing-1.0.jar'
        private HttpServer server
        private ExecutorService executor
        private final Resources resources = new Resources()

        @Override
        protected void before() {
            server = HttpServer.create(new InetSocketAddress(0), 0)
            executor = Executors.newCachedThreadPool()
            server.setExecutor(executor)
            server.createContext("/", new HttpHandler() {
                @Override
                void handle(HttpExchange exchange) throws IOException {
                    try {
                        String method = exchange.requestMethod
                        String path = exchange.requestURI.path
                        println "* Handling $method $path"
                        if (method == GET_METHOD && path == METADATA_FILE_PATH) {
                            writeResource(exchange, resources.ivy, true)
                        } else if (method == HEAD_METHOD && path == METADATA_FILE_PATH) {
                            writeResource(exchange, resources.ivy, false)
                        } else if (method == GET_METHOD && path == JAR_FILE_PATH) {
                            writeResource(exchange, resources.jar, true)
                        } else if (method == HEAD_METHOD && path == JAR_FILE_PATH) {
                            writeResource(exchange, resources.jar, false)
                        } else {
                            exchange.sendResponseHeaders(404, -1)
                        }
                    } finally {
                        exchange.close()
                    }
                }
            })
            server.start()
        }

        @Override
        protected void after() {
            server.stop(0)
            executor.shutdownNow()
        }

        private static void writeResource(HttpExchange exchange, Resource resource, boolean includeBody) {
            exchange.responseHeaders.set("Last-Modified", httpDate(resource.lastModified))
            exchange.responseHeaders.set("Content-Type", resource.contentType)
            exchange.sendResponseHeaders(200, resource.contentLength)
            if (includeBody) {
                OutputStream out = exchange.responseBody
                resource.writeContentTo(out)
                out.close()
            }
        }

        private static String httpDate(long millis) {
            def format = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("GMT")
            return format.format(new Date(millis))
        }

        URI getUri() {
            return new URI("http://127.0.0.1:${server.address.port}/")
        }
    }

    static class Resources {
        private final Object lock = new Object()
        private int count
        private Resource ivy
        private Resource jar
        private final IvyFileGenerator ivyGenerator = new IvyFileGenerator()
        private final JarFileGenerator jarGenerator = new JarFileGenerator()

        Resource getIvy() {
            synchronized (lock) {
                maybeReset()
                if (ivy == null) {
                    ivy = ivyGenerator.regenerate()
                }
                return ivy
            }
        }

        Resource getJar() {
            synchronized (lock) {
                maybeReset()
                if (jar == null) {
                    jar = jarGenerator.regenerate()
                }
                return jar
            }
        }

        private void maybeReset() {
            count++
            if (count > 4) {
                println "*** RESET"
                count = 0
                ivy = null
                jar = null
            }
        }
    }

    static class Resource {
        final long lastModified
        final byte[] content
        final String contentType

        Resource(byte[] content, long lastModified, String contentType) {
            this.content = content
            this.lastModified = lastModified
            this.contentType = contentType
        }

        int getContentLength() {
            content.length
        }

        void writeContentTo(OutputStream outputStream) {
            outputStream.write(content)
        }
    }

    static abstract class ResourceGenerator {
        private final Random random = new Random()

        Resource regenerate() {
            def str = new ByteArrayOutputStream()
            generateContent(str)
            def content = str.toByteArray()
            def lastModified = System.currentTimeMillis()
            return new Resource(content, lastModified, contentType)
        }

        // Writes a long string of text encoded with the system encoding to the given output stream
        protected void writeLongString(OutputStream outputStream) {
            for (int i = 0; i < 25000; i++) {
                outputStream.write(String.valueOf(random.nextInt()).bytes)
            }
        }

        protected abstract String getContentType()

        protected abstract void generateContent(OutputStream outputStream)
    }

    static class IvyFileGenerator extends ResourceGenerator {
        @Override
        String getContentType() {
            return "text/xml"
        }

        @Override
        void generateContent(OutputStream outputStream) {
            outputStream << '''
<ivy-module version="1.0">
    <info organisation="org.gradle" module="changing" revision="1.0"/>
    <!--
'''
            writeLongString(outputStream)
            outputStream << '''
-->
</ivy-module>
'''
        }
    }

    static class JarFileGenerator extends ResourceGenerator {
        @Override
        String getContentType() {
            return "application/java-archive"
        }

        @Override
        void generateContent(OutputStream outputStream) {
            def zipStream = new ZipOutputStream(outputStream)
            zipStream.putNextEntry(new ZipEntry("a"))
            writeLongString(zipStream)
            zipStream.closeEntry()
            zipStream.putNextEntry(new ZipEntry("b"))
            writeLongString(zipStream)
            zipStream.closeEntry()
            zipStream.finish()
        }
    }
}
