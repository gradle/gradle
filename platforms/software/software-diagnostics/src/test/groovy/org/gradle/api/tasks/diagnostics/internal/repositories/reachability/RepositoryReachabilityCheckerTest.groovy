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
package org.gradle.api.tasks.diagnostics.internal.repositories.reachability

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportContentFilter
import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportRepository
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryRole
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryType
import org.gradle.util.Path
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

import static org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryDeclarationSite.Scope.PROJECT

class RepositoryReachabilityCheckerTest extends Specification {
    @AutoCleanup("stop")
    LightweightHttpServer server = new LightweightHttpServer()

    def checker = new RepositoryReachabilityChecker(Duration.ofSeconds(5))

    def "REACHABLE for 200 response on HEAD"() {
        given:
        server.start({ exchange -> respond(exchange, 200) })
        def repo = makeRepo(server.uri())

        when:
        def result = checker.check([repo], false)

        then:
        result[server.uri()] == ReachabilityStatus.REACHABLE
    }

    def "REACHABLE for 3xx redirect"() {
        given:
        // HttpClient follows redirects (NORMAL) by default but only for absolute targets.
        // We just return a 302 with a location pointing at the same server so the follow yields 200.
        server.start({ exchange ->
            if (exchange.requestURI.path == "/redirected") {
                respond(exchange, 200)
            } else {
                exchange.responseHeaders.add("Location", server.uri() + "redirected")
                respond(exchange, 302)
            }
        })
        def repo = makeRepo(server.uri())

        when:
        def result = checker.check([repo], false)

        then:
        result[server.uri()] == ReachabilityStatus.REACHABLE
    }

    def "UNAUTHORIZED for #status"(int status) {
        given:
        server.start({ exchange -> respond(exchange, status) })
        def repo = makeRepo(server.uri())

        when:
        def result = checker.check([repo], false)

        then:
        result[server.uri()] == ReachabilityStatus.UNAUTHORIZED

        where:
        status << [401, 403]
    }

    def "UNREACHABLE for #status after GET fallback also fails with 4xx/5xx"(int status) {
        given:
        server.start({ exchange -> respond(exchange, status) })
        def repo = makeRepo(server.uri())

        when:
        def result = checker.check([repo], false)

        then:
        result[server.uri()] == ReachabilityStatus.UNREACHABLE

        where:
        status << [404, 500, 501]
    }

    def "REACHABLE via 405-on-HEAD, 200-on-GET fallback"() {
        given:
        server.start({ exchange ->
            if (exchange.requestMethod == "HEAD") {
                respond(exchange, 405)
            } else {
                respond(exchange, 200)
            }
        })
        def repo = makeRepo(server.uri())

        when:
        def result = checker.check([repo], false)

        then:
        result[server.uri()] == ReachabilityStatus.REACHABLE
    }

    def "REACHABLE via widened 4xx/5xx-on-HEAD, 200-on-GET fallback for status #headStatus"(int headStatus) {
        given:
        server.start({ exchange ->
            if (exchange.requestMethod == "HEAD") {
                respond(exchange, headStatus)
            } else {
                respond(exchange, 200)
            }
        })
        def repo = makeRepo(server.uri())

        when:
        def result = checker.check([repo], false)

        then:
        result[server.uri()] == ReachabilityStatus.REACHABLE

        where:
        headStatus << [400, 501]
    }

    def "UNREACHABLE when HEAD returns 500 and GET also returns 500"() {
        given:
        server.start({ exchange -> respond(exchange, 500) })
        def repo = makeRepo(server.uri())

        when:
        def result = checker.check([repo], false)

        then:
        result[server.uri()] == ReachabilityStatus.UNREACHABLE
    }

    def "MALFORMED_URL when location fails URI parsing"() {
        given:
        // Force probeable (http scheme) prefix then invalid [bracket to fail URI parsing at probe time.
        def bad = "http://[not-a-url/"
        def repo = makeRepo(bad)

        when:
        def result = checker.check([repo], false)

        then:
        result[bad] == ReachabilityStatus.MALFORMED_URL
    }

    def "MAVEN_LOCAL repositories are skipped"() {
        given:
        def repo = new ReportRepository(
            "local", RepositoryType.MAVEN_LOCAL, "file:///tmp/mvn",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories"))

        when:
        def result = checker.check([repo], false)

        then:
        result.isEmpty()
    }

    def "FLAT_DIR repositories are skipped"() {
        given:
        def repo = new ReportRepository(
            "flat", RepositoryType.FLAT_DIR, "dirs:[/tmp/libs]",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories"))

        when:
        def result = checker.check([repo], false)

        then:
        result.isEmpty()
    }

    def "non-http scheme is skipped"() {
        given:
        def repo = new ReportRepository(
            "file", RepositoryType.MAVEN, "file:///tmp/x",
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories"))

        when:
        def result = checker.check([repo], false)

        then:
        result.isEmpty()
    }

    def "offline mode returns empty map regardless of inputs"() {
        given:
        def repo = makeRepo("http://anywhere.example.com/")

        when:
        def result = checker.check([repo], true)

        then:
        result.isEmpty()
    }

    def "deduplicates: two repos with same location produce only one probe"() {
        given:
        def counter = new AtomicInteger(0)
        server.start({ exchange ->
            counter.incrementAndGet()
            respond(exchange, 200)
        })
        def r1 = makeRepo(server.uri())
        def r2 = makeRepo(server.uri())

        when:
        def result = checker.check([r1, r2], false)

        then:
        result.size() == 1
        result[server.uri()] == ReachabilityStatus.REACHABLE
        counter.get() == 1
    }

    private static ReportRepository makeRepo(String location) {
        return new ReportRepository(
            "r", RepositoryType.MAVEN, location,
            true, [], false, ReportContentFilter.EMPTY,
            [RepositoryRole.PROJECT_DEPENDENCIES] as Set,
            new RepositoryDeclarationSite(PROJECT, Path.path(":app"), "repositories"))
    }

    private static void respond(HttpExchange exchange, int status) {
        exchange.sendResponseHeaders(status, -1)
        exchange.close()
    }

    /**
     * Minimal HTTP server fixture so these unit tests do not depend on Jetty or on the
     * integration-test HttpServer fixture (which lives in testFixtures we don't consume here).
     */
    static class LightweightHttpServer {
        HttpServer underlying
        int port

        void start(HttpHandler handler) {
            underlying = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
            underlying.createContext("/", handler)
            underlying.start()
            port = underlying.address.port
        }

        String uri() {
            return "http://127.0.0.1:${port}/"
        }

        void stop() {
            if (underlying != null) {
                underlying.stop(0)
            }
        }
    }
}
