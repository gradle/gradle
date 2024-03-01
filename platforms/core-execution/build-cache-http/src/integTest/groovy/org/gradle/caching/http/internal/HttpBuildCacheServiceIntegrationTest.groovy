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

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.caching.http.HttpBuildCache
import org.gradle.caching.internal.operations.BuildCacheRemoteStoreBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.internal.deprecation.Documentation
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.test.fixtures.keystore.TestKeyStore

@IntegrationTestTimeout(120)
class HttpBuildCacheServiceIntegrationTest extends HttpBuildCacheFixture {

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

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        settingsFile << withHttpBuildCacheServer()

        buildFile << """
            apply plugin: "java"
        """

        file("src/main/java/Hello.java") << ORIGINAL_HELLO_WORLD
        file("src/main/resources/resource.properties") << """
            test=true
        """
    }

    def "no task is re-executed when inputs are unchanged"() {
        when:
        withBuildCache().run "jar"
        then:
        noneSkipped()

        expect:
        withBuildCache().run "clean"

        when:
        withBuildCache().run "jar"
        then:
        skipped ":compileJava"
    }

    def "outputs are correctly loaded from cache"() {
        buildFile << """
            apply plugin: "application"
            application {
                mainClass = "Hello"
            }
        """
        withBuildCache().run "run"
        withBuildCache().run "clean"
        expect:
        withBuildCache().run "run"
    }

    def "tasks get cached when source code changes back to previous state"() {
        expect:
        withBuildCache().run "jar" assertTaskNotSkipped ":compileJava" assertTaskNotSkipped ":jar"

        when:
        file("src/main/java/Hello.java").text = CHANGED_HELLO_WORLD
        then:
        withBuildCache().run "jar" assertTaskNotSkipped ":compileJava" assertTaskNotSkipped ":jar"

        when:
        file("src/main/java/Hello.java").text = ORIGINAL_HELLO_WORLD
        then:
        withBuildCache().run "jar"
        result.assertTaskSkipped ":compileJava"
    }

    def "clean doesn't get cached"() {
        withBuildCache().run "assemble"
        withBuildCache().run "clean"
        withBuildCache().run "assemble"
        when:
        withBuildCache().run "clean"
        then:
        executedAndNotSkipped ":clean"
    }

    def "cacheable task with cache disabled doesn't get cached"() {
        buildFile << """
            compileJava.outputs.cacheIf { false }
        """

        withBuildCache().run "compileJava"
        withBuildCache().run "clean"

        when:
        withBuildCache().run "compileJava"
        then:
        // :compileJava is not cached, but :jar is still cached as its inputs haven't changed
        executedAndNotSkipped ":compileJava"
    }

    def "non-cacheable task with cache enabled gets cached"() {
        file("input.txt") << "data"
        buildFile << """
            class NonCacheableTask extends DefaultTask {
                @InputFile inputFile
                @OutputFile outputFile

                @TaskAction copy() {
                    outputFile.parentFile.mkdirs()
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
        withBuildCache().run "jar"
        then:
        executedAndNotSkipped ":customTask"

        when:
        withBuildCache().run "clean"
        withBuildCache().run "jar"
        then:
        skipped ":customTask"
    }

    def "url can be specified with trailing slash"() {
        httpBuildCacheServer.start()
        def buildCacheUrl = URI.create("${httpBuildCacheServer.uri}/")
        settingsFile.text = useHttpBuildCache(buildCacheUrl)

        when:
        withBuildCache().run "jar"
        then:
        noneSkipped()

        expect:
        withBuildCache().run "clean"

        when:
        withBuildCache().run "jar"
        then:
        skipped ":compileJava"
    }

    def "credentials can be specified via DSL"() {
        httpBuildCacheServer.withBasicAuth("user", "pass")
        settingsFile << """
            buildCache {
                remote.credentials {
                    username = "user"
                    password = "pass"
                }
            }
        """

        when:
        withBuildCache().run "jar"
        then:
        noneSkipped()
        httpBuildCacheServer.authenticationAttempts == ['Basic'] as Set

        expect:
        withBuildCache().run "clean"

        when:
        withBuildCache().run "jar"
        then:
        skipped ":compileJava"
        httpBuildCacheServer.authenticationAttempts == ['Basic'] as Set
    }

    def "credentials can be specified via URL"() {
        httpBuildCacheServer.withBasicAuth("user", 'pass%:-0]#')
        settingsFile.text = useHttpBuildCache(getUrlWithCredentials("user", 'pass%:-0]#'))

        when:
        withBuildCache().run "jar"
        then:
        noneSkipped()
        httpBuildCacheServer.authenticationAttempts == ['Basic'] as Set

        expect:
        withBuildCache().run "clean"

        when:
        httpBuildCacheServer.reset()
        httpBuildCacheServer.withBasicAuth("user", "pass%:-0]#")
        withBuildCache().run "jar"
        then:
        skipped ":compileJava"
        httpBuildCacheServer.authenticationAttempts == ['Basic'] as Set
    }

    def "credentials from DSL override credentials in URL"() {
        httpBuildCacheServer.withBasicAuth("user", "pass")
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
        withBuildCache().run "jar"
        then:
        noneSkipped()
        httpBuildCacheServer.authenticationAttempts == ['Basic'] as Set
    }

    def "can use a self-signed certificate with allowUntrusted"() {
        def keyStore = TestKeyStore.init(file('ssl-keystore'))
        keyStore.enableSslWithServerCert(httpBuildCacheServer)
        settingsFile.text = useHttpBuildCache(httpBuildCacheServer.uri)
        settingsFile << """
            buildCache {
                remote {
                    allowUntrustedServer = true
                }
            }
        """.stripIndent()

        when:
        withBuildCache().run "jar"
        succeeds "clean"
        withBuildCache().run "jar"

        then:
        skipped(":compileJava")
    }

    def "throws exception when using plain HTTP"() {
        when:
        httpBuildCacheServer.useHostname()
        settingsFile.text = useHttpBuildCache(httpBuildCacheServer.uri)

        then:
        withBuildCache().fails("jar")
            .assertHasCause("Using insecure protocols with remote build cache, without explicit opt-in, is unsupported.")
            .assertHasResolution("Switch remote build cache to a secure protocol (like HTTPS) or allow insecure protocols.")
            .assertHasResolution(Documentation.dslReference(HttpBuildCache, "allowInsecureProtocol").getConsultDocumentationMessage())
    }

    def "ssl certificate is validated"() {
        def keyStore = TestKeyStore.init(file('ssl-keystore'))
        keyStore.enableSslWithServerCert(httpBuildCacheServer)
        settingsFile.text = useHttpBuildCache(httpBuildCacheServer.uri)

        when:
        executer.withStackTraceChecksDisabled()
        withBuildCache().run "jar"

        then:
        noneSkipped()
        output.contains('PKIX path building failed: ')
    }

    private URI getUrlWithCredentials(String user, String password) {
        def uri = httpBuildCacheServer.uri
        return new URI(uri.getScheme(), "${user}:${password}", uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment())
    }

    def "build does not leak credentials in cache URL"() {
        httpBuildCacheServer.withBasicAuth("correct-username", "correct-password")
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
        withBuildCache().run "assemble"
        then:
        outputDoesNotContain("correct-username")
        outputDoesNotContain("correct-password")
    }

    def "incorrect credentials cause build to fail"() {
        httpBuildCacheServer.withBasicAuth("user", "pass")
        settingsFile << """
            buildCache {
                remote.credentials {
                    username = "incorrect-user"
                    password = "incorrect-pass"
                }
            }
        """

        when:
        executer.withStackTraceChecksDisabled()
        withBuildCache().run "jar"
        then:
        output.contains "response status 401: Unauthorized"
        // Make sure we don't log the password
        result.assertNotOutput("incorrect-pass")
    }

    def "unknown host causes the build cache to be disabled"() {
        settingsFile << """
            buildCache {
                remote {
                    url = "https://invalid.invalid/"
                }
            }
        """

        when:
        executer.withStacktraceEnabled()
        executer.withStackTraceChecksDisabled()
        withBuildCache().run "jar"

        then:
        output.contains("java.net.UnknownHostException")
        output.contains("invalid.invalid")
        output.contains("The remote build cache was disabled during the build due to errors.")
    }

    def "storing to cache does follow method preserving redirects"() {
        given:
        httpBuildCacheServer.cacheDir.createDir("redirect")
        httpBuildCacheServer.addResponder { req, res ->
            if (!req.requestURI.startsWith("/redirect")) {
                res.setHeader("location", "redirect$req.requestURI")
                res.setStatus(307)
                res.writer.close()
                false
            } else {
                true
            }
        }

        when:
        withBuildCache().run "jar"
        then:
        noneSkipped()
        and:
        // Only one store operation, not one per redirect
        compileJavaStoreOperations().size() == 1

        expect:
        withBuildCache().run "clean"

        when:
        withBuildCache().run "jar"
        then:
        skipped ":compileJava"
    }

    /**
     * This scenario represents a potentially misconfigured server trying to redirect writes, but using the wrong status to do so.
     * This is still potentially valid usage though, and is valid HTTP.
     * Theoretically, a service could accept the write and then redirect to another page that polls for the success of that write.
     */
    def "non method preserving redirects on write result in discarded write"() {
        given:
        httpBuildCacheServer.cacheDir.createDir("redirect")
        httpBuildCacheServer.addResponder { req, res ->
            if (req.method == "PUT") {
                res.setHeader("location", "/ok")
                res.setStatus(301)
                res.writer.close()
                false
            } else if (req.requestURI == "/ok") {
                res.setStatus(200)
                res.writer.close()
                false
            } else {
                true
            }
        }

        when:
        withBuildCache().run "jar"
        then:
        noneSkipped()

        expect:
        withBuildCache().run "clean"

        when:
        withBuildCache().run "jar"
        then:
        noneSkipped()
    }

    def "treats redirect loop as failure"() {
        given:
        httpBuildCacheServer.cacheDir.createDir("redirect")
        httpBuildCacheServer.addResponder { req, res ->
            res.setHeader("location", req.requestURI)
            res.setStatus(301)
            res.writer.close()
            false
        }

        when:
        executer.withStacktraceEnabled()
        executer.withStackTraceChecksDisabled()
        withBuildCache().run "jar"
        then:
        noneSkipped()

        and:
        output.contains("Could not load entry")
        output.contains("Circular redirect to")
    }

    def "treats too many redirects as failure"() {
        given:
        httpBuildCacheServer.cacheDir.createDir("redirect")
        httpBuildCacheServer.addResponder { req, res ->
            res.setHeader("location", "/r$req.requestURI")
            res.setStatus(301)
            res.writer.close()
            false
        }

        when:
        executer.withStacktraceEnabled()
        executer.withStackTraceChecksDisabled()
        withBuildCache().run "jar"
        then:
        noneSkipped()

        and:
        output.contains("Could not load entry")
        output.contains("Maximum redirects (10) exceeded")
    }

    def "can use expect continue"() {
        given:
        settingsFile << """
            buildCache {
                remote {
                    useExpectContinue = true
                }
            }
        """.stripIndent()

        when:
        withBuildCache().run "jar"
        then:
        noneSkipped()

        expect:
        withBuildCache().run "clean"

        when:
        withBuildCache().run "jar"
        then:
        skipped ":compileJava"
    }

    def "store can be rejected when using expect continue"() {
        given:
        settingsFile << """
            buildCache {
                remote {
                    useExpectContinue = true
                }
            }
        """.stripIndent()

        and:
        httpBuildCacheServer.addResponder { req, res ->
            if (req.method == "PUT") {
                assert req.getHeader("expect") == "100-continue"
                res.sendError(401)
                false
            } else {
                true
            }
        }

        when:
        executer.withStackTraceChecksDisabled()
        withBuildCache().run "jar"
        then:
        noneSkipped()
        and:
        def storeOps = compileJavaStoreOperations()
        storeOps.size() == 1
        storeOps.first().failure.contains("response status 401: Unauthorized")
    }

    private List<BuildOperationRecord> compileJavaStoreOperations() {
        buildOperations.all(BuildCacheRemoteStoreBuildOperationType) {
            buildOperations.parentsOf(it).any {
                it.hasDetailsOfType(ExecuteTaskBuildOperationType.Details) && it.details.taskPath == ":compileJava"
            }
        }
    }
}
