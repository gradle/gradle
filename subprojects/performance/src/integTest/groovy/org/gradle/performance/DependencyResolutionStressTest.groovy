/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.performance

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.mortbay.jetty.HttpHeaders
import org.mortbay.jetty.Server
import org.mortbay.jetty.bio.SocketConnector
import org.mortbay.jetty.handler.AbstractHandler
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DependencyResolutionStressTest extends Specification {
    @Rule TestNameTestDirectoryProvider workspace = new TestNameTestDirectoryProvider()
    GradleDistribution distribution = new GradleDistribution(workspace)
    @Rule StressHttpServer server = new StressHttpServer()
    @Rule ConcurrentTestUtil concurrent = new ConcurrentTestUtil()

    def setup() {
        concurrent.shortTimeout = 180000
    }

    def "handles concurrent access to changing artifacts"() {
        expect:
        4.times { count ->
            concurrent.start {
                def buildDir = distribution.file(count)
                buildDir.file('build.gradle') << """
import java.util.zip.*

repositories {
    ivy { url '${server.uri}' }
}

configurations {
    compile
}

dependencies {
    compile 'org.gradle:changing:1.0'
}

task check << {
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
        """

                GradleDistributionExecuter executer = distribution.executer()
                executer.requireGradleHome(true).requireOwnGradleUserHomeDir()
                10.times {
                    executer.inDirectory(buildDir).withArgument("--refresh-dependencies").withTasks('check').run()
                }
            }
        }
        concurrent.finished()
    }

    static class StressHttpServer extends ExternalResource {
        final Server server = new Server(0)
        final Resources resources = new Resources()

        @Override
        protected void before() {
            server.addConnector(new SocketConnector())
            server.addHandler(new AbstractHandler() {
                void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                    println "* Handling $request.method $request.pathInfo"
                    if (request.method == 'GET' && request.pathInfo == '/org.gradle/changing/1.0/ivy-1.0.xml') {
                        handleGetIvy(request, response)
                        request.handled = true
                    } else if (request.method == 'HEAD' && request.pathInfo == '/org.gradle/changing/1.0/ivy-1.0.xml') {
                        handleHeadIvy(request, response)
                        request.handled = true
                    } else if (request.method == 'GET' && request.pathInfo == '/org.gradle/changing/1.0/changing-1.0.jar') {
                        handleGetJar(request, response)
                        request.handled = true
                    } else if (request.method == 'HEAD' && request.pathInfo == '/org.gradle/changing/1.0/changing-1.0.jar') {
                        handleHeadJar(request, response)
                        request.handled = true
                    }
                }
            })
            server.start()
        }

        private void handleGetIvy(HttpServletRequest request, HttpServletResponse response) {
            println "* GET IVY FILE"
            def ivy = resources.ivy
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, ivy.lastModified)
            response.setContentLength(ivy.contentLength)
            response.setContentType(ivy.contentType)
            ivy.writeContentTo(response.outputStream)
        }

        private void handleHeadIvy(HttpServletRequest request, HttpServletResponse response) {
            println "* HEAD IVY FILE"
            def ivy = resources.ivy
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, ivy.lastModified)
            response.setContentLength(ivy.contentLength)
            response.setContentType(ivy.contentType)
        }

        private void handleGetJar(HttpServletRequest request, HttpServletResponse response) {
            println "* GET JAR"
            def jar = resources.jar
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, jar.lastModified)
            response.setContentLength(jar.contentLength)
            response.setContentType(jar.contentType)
            jar.writeContentTo(response.outputStream)
        }

        private void handleHeadJar(HttpServletRequest request, HttpServletResponse response) {
            println "* HEAD JAR"
            def jar = resources.jar
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, jar.lastModified)
            response.setContentLength(jar.contentLength)
            response.setContentType(jar.contentType)
        }

        @Override
        protected void after() {
            server.stop()
        }

        URI getUri() {
            return new URI("http://localhost:${server.connectors[0].localPort}/")
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
        private final Random random = new Random();

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
            zipStream.putNextEntry(new ZipEntry("b"))
            writeLongString(zipStream)
            zipStream.finish()
        }
    }
}
