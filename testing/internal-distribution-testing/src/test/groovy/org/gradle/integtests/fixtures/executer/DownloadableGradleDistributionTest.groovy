/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.fixtures.executer

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloadableGradleDistributionTest extends Specification {

    private static final String VERSION = "8.14"

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    HttpServer server
    List<String> requests = new CopyOnWriteArrayList<>()
    byte[] validZip = createDistributionZip()

    def setup() {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.start()
    }

    def cleanup() {
        server.stop(0)
    }

    def "retries when the server returns a truncated zip with a matching Content-Length"() {
        given:
        serve("/primary", [truncatedWithMatchingLength(), complete()])
        def distribution = distribution("/primary", "/fallback")

        when:
        def gradleHome = distribution.gradleHomeDir

        then:
        gradleHome.file("bin/gradle").text == "#!/bin/sh"
        distribution.binDistribution.bytes == validZip
        requests == ["/primary", "/primary"]
    }

    def "uses the fallback URL for the last attempt"() {
        given:
        serve("/primary", [truncatedWithMatchingLength()] * DownloadableGradleDistribution.MAX_DOWNLOAD_ATTEMPTS)
        serve("/fallback", [complete()])
        def distribution = distribution("/primary", "/fallback")

        when:
        def gradleHome = distribution.gradleHomeDir

        then:
        gradleHome.file("bin/gradle").text == "#!/bin/sh"
        requests == ["/primary"] * (DownloadableGradleDistribution.MAX_DOWNLOAD_ATTEMPTS - 1) + ["/fallback"]
    }

    def "fails and leaves no partial installation when every attempt is corrupt"() {
        given:
        serve("/primary", [truncatedWithMatchingLength()] * DownloadableGradleDistribution.MAX_DOWNLOAD_ATTEMPTS)
        serve("/fallback", [truncatedWithMatchingLength()])
        def distribution = distribution("/primary", "/fallback")
        def versionDir = tmpDir.testDirectory.file("versions")

        when:
        distribution.gradleHomeDir

        then:
        thrown(Throwable)
        requests.size() == DownloadableGradleDistribution.MAX_DOWNLOAD_ATTEMPTS
        !versionDir.file("gradle-$VERSION-bin.zip").exists()
        !versionDir.file("gradle-$VERSION-bin.ok").exists()
        !versionDir.file("gradle-$VERSION").exists()
    }

    def "download URL uses the services base URL override when it is set"() {
        def uri = new URI("https://services.gradle.org/distributions/gradle-8.14-bin.zip")

        expect:
        ReleasedGradleDistribution.withBaseUrlOverride(uri, override).toString() == expected

        where:
        override                                    | expected
        null                                        | "https://services.gradle.org/distributions/gradle-8.14-bin.zip"
        "  "                                        | "https://services.gradle.org/distributions/gradle-8.14-bin.zip"
        "http://cache.local/services.gradle.org"    | "http://cache.local/services.gradle.org/distributions/gradle-8.14-bin.zip"
        " http://cache.local/services.gradle.org/ " | "http://cache.local/services.gradle.org/distributions/gradle-8.14-bin.zip"
    }

    private DownloadableGradleDistribution distribution(String primaryPath, String fallbackPath) {
        new TestDistribution(tmpDir.testDirectory.file("versions"), url(primaryPath), url(fallbackPath))
    }

    private URL url(String path) {
        new URL("http://127.0.0.1:${server.address.port}$path")
    }

    private void serve(String path, List<Closure> responses) {
        def remaining = new ArrayDeque<Closure>(responses)
        server.createContext(path) { HttpExchange exchange ->
            requests << path
            def response = remaining.poll()
            if (response == null) {
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
            } else {
                response(exchange)
            }
        }
    }

    private Closure complete() {
        return { HttpExchange exchange ->
            exchange.sendResponseHeaders(200, validZip.length)
            exchange.responseBody.write(validZip)
            exchange.close()
        }
    }

    private Closure truncatedWithMatchingLength() {
        def truncated = Arrays.copyOf(validZip, validZip.length.intdiv(2) as int)
        return { HttpExchange exchange ->
            exchange.sendResponseHeaders(200, truncated.length)
            exchange.responseBody.write(truncated)
            exchange.close()
        }
    }

    private static byte[] createDistributionZip() {
        def bytes = new ByteArrayOutputStream()
        new ZipOutputStream(bytes).withCloseable { zip ->
            zip.putNextEntry(new ZipEntry("gradle-$VERSION/"))
            zip.closeEntry()
            zip.putNextEntry(new ZipEntry("gradle-$VERSION/bin/"))
            zip.closeEntry()
            zip.putNextEntry(new ZipEntry("gradle-$VERSION/bin/gradle"))
            zip.write("#!/bin/sh".bytes)
            zip.closeEntry()
            zip.putNextEntry(new ZipEntry("gradle-$VERSION/lib/"))
            zip.closeEntry()
            zip.putNextEntry(new ZipEntry("gradle-$VERSION/lib/padding.bin"))
            def padding = new byte[64 * 1024]
            new Random(42).nextBytes(padding)
            zip.write(padding)
            zip.closeEntry()
        }
        bytes.toByteArray()
    }

    private static class TestDistribution extends DownloadableGradleDistribution {
        private final URL downloadUrl
        private final URL fallbackUrl

        TestDistribution(TestFile versionDir, URL downloadUrl, URL fallbackUrl) {
            super(VERSION, versionDir)
            this.downloadUrl = downloadUrl
            this.fallbackUrl = fallbackUrl
        }

        @Override
        protected URL getDownloadURL() {
            downloadUrl
        }

        @Override
        protected URL getFallbackDownloadURL() {
            fallbackUrl
        }
    }
}
