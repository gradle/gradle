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

package org.gradle.caching.http.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpBuildCache
import org.junit.Rule
import spock.lang.Timeout

@Timeout(120)
class HttpBuildCacheServiceIntegrationTest extends AbstractIntegrationSpec {

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
    HttpBuildCache httpBuildCache = new HttpBuildCache(testDirectoryProvider)

    def setup() {
        httpBuildCache.start()
        settingsFile << useHttpBuildCache(httpBuildCache.uri)

        buildFile << """
            apply plugin: "java"
        """

        file("src/main/java/Hello.java") << ORIGINAL_HELLO_WORLD
        file("src/main/resources/resource.properties") << """
            test=true
        """
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

    def "no task is re-executed when inputs are unchanged"() {
        when:
        withHttpBuildCache().succeeds "jar"
        then:
        skippedTasks.empty

        expect:
        withHttpBuildCache().succeeds "clean"

        when:
        withHttpBuildCache().succeeds "jar"
        then:
        skippedTasks.containsAll ":compileJava"
    }

    def "outputs are correctly loaded from cache"() {
        buildFile << """
            apply plugin: "application"
            mainClassName = "Hello"
        """
        withHttpBuildCache().run "run"
        withHttpBuildCache().run "clean"
        expect:
        withHttpBuildCache().succeeds "run"
    }

    def "tasks get cached when source code changes back to previous state"() {
        expect:
        withHttpBuildCache().succeeds "jar" assertTaskNotSkipped ":compileJava" assertTaskNotSkipped ":jar"

        when:
        file("src/main/java/Hello.java").text = CHANGED_HELLO_WORLD
        then:
        withHttpBuildCache().succeeds "jar" assertTaskNotSkipped ":compileJava" assertTaskNotSkipped ":jar"

        when:
        file("src/main/java/Hello.java").text = ORIGINAL_HELLO_WORLD
        then:
        withHttpBuildCache().succeeds "jar"
        result.assertTaskSkipped ":compileJava"
    }

    def "clean doesn't get cached"() {
        withHttpBuildCache().run "assemble"
        withHttpBuildCache().run "clean"
        withHttpBuildCache().run "assemble"
        when:
        withHttpBuildCache().succeeds "clean"
        then:
        nonSkippedTasks.contains ":clean"
    }

    def "cacheable task with cache disabled doesn't get cached"() {
        buildFile << """
            compileJava.outputs.cacheIf { false }
        """

        withHttpBuildCache().run "compileJava"
        withHttpBuildCache().run "clean"

        when:
        withHttpBuildCache().succeeds "compileJava"
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
        withHttpBuildCache().run "jar"
        then:
        nonSkippedTasks.contains ":customTask"

        when:
        withHttpBuildCache().run "clean"
        withHttpBuildCache().succeeds "jar"
        then:
        skippedTasks.contains ":customTask"
    }

    def "credentials can be specified via DSL"() {
        httpBuildCache.withBasicAuth("user", "pass")
        settingsFile << """
            buildCache {
                remote.credentials {
                    username = "user"
                    password = "pass"
                }
            }
        """

        when:
        withHttpBuildCache().succeeds "jar"
        then:
        skippedTasks.empty
        httpBuildCache.authenticationAttempts == ['Basic'] as Set

        expect:
        withHttpBuildCache().succeeds "clean"

        when:
        withHttpBuildCache().succeeds "jar"
        then:
        skippedTasks.containsAll ":compileJava"
        httpBuildCache.authenticationAttempts == ['Basic'] as Set
    }

    def "credentials can be specified via URL"() {
        httpBuildCache.withBasicAuth("user", "pass")
        settingsFile.text = useHttpBuildCache(getUrlWithCredentials("user", "pass"))

        when:
        withHttpBuildCache().succeeds "jar"
        then:
        skippedTasks.empty
        httpBuildCache.authenticationAttempts == ['None', 'Basic'] as Set

        expect:
        withHttpBuildCache().succeeds "clean"

        when:
        httpBuildCache.reset()
        httpBuildCache.withBasicAuth("user", "pass")
        withHttpBuildCache().succeeds "jar"
        then:
        skippedTasks.containsAll ":compileJava"
        httpBuildCache.authenticationAttempts == ['None', 'Basic'] as Set
    }

    def "credentials from DSL override credentials in URL"() {
        httpBuildCache.withBasicAuth("user", "pass")
        settingsFile.text = useHttpBuildCache(getUrlWithCredentials("user", "wrongPass"))
        settingsFile << """
            buildCache {
                remote.credentials {
                    username = "user"
                    password = "pass"
                }
            }
        """

        when:
        withHttpBuildCache().succeeds "jar"
        then:
        skippedTasks.empty
        httpBuildCache.authenticationAttempts == ['Basic'] as Set
    }

    private URI getUrlWithCredentials(String user, String password) {
        def uri = httpBuildCache.uri
        return new URI(uri.getScheme(), "${user}:${password}", uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment())
    }

    def "build does not leak credentials in cache URL"() {
        httpBuildCache.withBasicAuth("correct-username", "correct-password")
        settingsFile << """
            buildCache {
                remote.credentials {
                    username = "correct-username"
                    password = "correct-password"
                }
            }
        """

        when:
        executer.withArgument("--info")
        withHttpBuildCache().succeeds "assemble"
        then:
        !result.output.contains("correct-username")
        !result.output.contains("correct-password")
    }

    def "incorrect credentials cause build to fail"() {
        httpBuildCache.withBasicAuth("user", "pass")
        settingsFile << """
            buildCache {
                remote.credentials {
                    username = "incorrect-user"
                    password = "incorrect-pass"
                }
            }
        """

        when:
        withHttpBuildCache().fails "jar"
        then:
        failureDescriptionContains "response status 401: Unauthorized"
        // Make sure we don't log the password
        !output.contains("incorrect-pass")
        !errorOutput.contains("incorrect-pass")
    }

    def "unknown host causes the build to fail"() {
        settingsFile << """        
            buildCache {
                remote {
                    url = "http://invalid.invalid/"
                }
            }
        """

        when:
        executer.withStackTraceChecksDisabled()
        withHttpBuildCache().fails "jar"

        then:
        failure.error.contains("java.net.UnknownHostException: invalid.invalid")
    }

    def withHttpBuildCache() {
        executer.withBuildCacheEnabled()
        this
    }
}
