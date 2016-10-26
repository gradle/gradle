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

package org.gradle.cache.tasks.http

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.ports.ReleasingPortAllocator
import org.junit.Rule
import org.mortbay.jetty.Server
import org.mortbay.jetty.bio.SocketConnector
import org.mortbay.jetty.webapp.WebAppContext
import org.mortbay.servlet.RestFilter

class HttpTaskOutputCacheIntegrationTest extends AbstractIntegrationSpec {

    static final String ORIGINAL_HELLO_WORLD = """
            public class Hello {
                public static void main(String... args) {
                    System.out.println("Hello World!");
                }
            }
        """
    static final String CHANGED_HELLO_WORLD = """
            public class Hello {
                public static void main(String... args) {
                    System.out.println("Hello World with Changes!");
                }
            }
        """

    @Rule
    ReleasingPortAllocator portAllocator = new ReleasingPortAllocator()
    Server server

    def setup() {
        def cacheDir = file(".gradle/cache-dir")
        cacheDir.mkdirs()

        def port = portAllocator.assignPort()
        println "Using port $port"
        server = new Server()
        def connector = new SocketConnector()
        connector.setPort(port)
        server.setConnectors(connector)

        def webapp = new WebAppContext()
        webapp.contextPath = "/cache"
        webapp.resourceBase = cacheDir.absolutePath
        webapp.addFilter(RestFilter, "/*", 1)

        server.setHandler(webapp)
        server.start()

        file("init-cache.gradle") << """
            import org.gradle.cache.tasks.http.*

            taskCaching {
                useCacheFactory(new HttpTaskOutputCacheFactory(URI.create("http://localhost:$port/cache/")))
            }
        """

        buildFile << """
            apply plugin: "java"
        """

        file("src/main/java/Hello.java") << ORIGINAL_HELLO_WORLD
        file("src/main/resources/resource.properties") << """
            test=true
        """
    }

    def cleanup() {
        server.stop()
    }

    def "no task is re-executed when inputs are unchanged"() {
        when:
        withTaskCache().succeeds  "jar"
        then:
        skippedTasks.empty

        expect:
        withTaskCache().succeeds  "clean"

        when:
        withTaskCache().succeeds  "jar"
        then:
        skippedTasks.containsAll ":compileJava", ":jar"
    }

    def "outputs are correctly loaded from cache"() {
        buildFile << """
            apply plugin: "application"
            mainClassName = "Hello"
        """
        withTaskCache().run  "run"
        withTaskCache().run  "clean"
        expect:
        withTaskCache().succeeds  "run"
    }

    def "tasks get cached when source code changes without changing the compiled output"() {
        when:
        withTaskCache().succeeds  "assemble"
        then:
        skippedTasks.empty

        file("src/main/java/Hello.java") << """
            // Change to source file without compiled result change
        """
        withTaskCache().succeeds  "clean"

        when:
        withTaskCache().succeeds  "assemble"
        then:
        nonSkippedTasks.contains ":compileJava"
        skippedTasks.contains ":jar"
    }

    def "tasks get cached when source code changes back to previous state"() {
        expect:
        withTaskCache().succeeds "jar" assertTaskNotSkipped ":compileJava" assertTaskNotSkipped ":jar"

        when:
        file("src/main/java/Hello.java").text = CHANGED_HELLO_WORLD
        then:
        withTaskCache().succeeds  "jar" assertTaskNotSkipped ":compileJava" assertTaskNotSkipped ":jar"

        when:
        file("src/main/java/Hello.java").text = ORIGINAL_HELLO_WORLD
        then:
        withTaskCache().succeeds  "jar"
        result.assertTaskSkipped ":compileJava"
        result.assertTaskSkipped ":jar"
    }

    def "jar tasks get cached even when output file is changed"() {
        file("settings.gradle") << "rootProject.name = 'test'"
        buildFile << """
            if (file("toggle.txt").exists()) {
                jar {
                    destinationDir = file("\$buildDir/other-jar")
                    baseName = "other-jar"
                }
            }
        """

        expect:
        withTaskCache().succeeds  "assemble"
        skippedTasks.empty
        file("build/libs/test.jar").isFile()

        withTaskCache().succeeds  "clean"
        !file("build/libs/test.jar").isFile()

        file("toggle.txt").touch()

        withTaskCache().succeeds  "assemble"
        skippedTasks.contains ":jar"
        !file("build/libs/test.jar").isFile()
        file("build/other-jar/other-jar.jar").isFile()
    }

    def "clean doesn't get cached"() {
        withTaskCache().run  "assemble"
        withTaskCache().run  "clean"
        withTaskCache().run  "assemble"
        when:
        withTaskCache().succeeds  "clean"
        then:
        nonSkippedTasks.contains ":clean"
    }

    def "cacheable task with cache disabled doesn't get cached"() {
        buildFile << """
            compileJava.outputs.cacheIf { false }
        """

        withTaskCache().run  "compileJava"
        withTaskCache().run  "clean"

        when:
        withTaskCache().succeeds  "compileJava"
        then:
        // :compileJava is not cached, but :jar is still cached as its inputs haven't changed
        nonSkippedTasks.contains ":compileJava"
    }

    def "non-cacheable task with cache enabled gets cached"() {
        file("input.txt") << "data"
        buildFile << """
            class NonCacheableTask extends DefaultTask {
                @InputFile inputFile
                @OutputFile outputFile

                @TaskAction copy() {
                    project.mkdir outputFile.parentFile
                    outputFile.text = inputFile.text
                }
            }
            task customTask(type: NonCacheableTask) {
                inputFile = file("input.txt")
                outputFile = file("\$buildDir/output.txt")
                outputs.cacheIf { true }
            }
            compileJava.dependsOn customTask
        """

        when:
        withTaskCache().run  "jar"
        then:
        nonSkippedTasks.contains ":customTask"

        when:
        withTaskCache().run  "clean"
        withTaskCache().succeeds  "jar"
        then:
        skippedTasks.contains ":customTask"
    }

    HttpTaskOutputCacheIntegrationTest withTaskCache() {
        executer.withTaskCacheEnabled().withArgument "-I" withArgument "init-cache.gradle"
        this
    }
}
