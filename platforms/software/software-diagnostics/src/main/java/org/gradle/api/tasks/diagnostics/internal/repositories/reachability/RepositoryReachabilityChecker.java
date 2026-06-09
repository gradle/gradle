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
package org.gradle.api.tasks.diagnostics.internal.repositories.reachability;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.ReportRepository;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryType;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Issues lightweight HEAD (or GET fallback) probes to unique remote repository URLs to classify
 * their current reachability. Intended for diagnostic purposes only — not for build resolution.
 *
 * <p>Credentials are not sent; any URL gated behind HTTP authentication will be reported as
 * {@link ReachabilityStatus#UNAUTHORIZED}.
 *
 * <p>Probes run in parallel across a small fixed thread pool. Each probe respects a per-URL
 * timeout (default 5 seconds) that bounds total wall time for a single URL regardless of
 * fallback retries.
 */
@NullMarked
public final class RepositoryReachabilityChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryReachabilityChecker.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int PROBE_THREAD_POOL_SIZE = 8;
    private final Duration timeout;

    public RepositoryReachabilityChecker(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * Convenience constructor using the default 5-second per-URL timeout.
     */
    public static RepositoryReachabilityChecker withDefaultTimeout() {
        return new RepositoryReachabilityChecker(DEFAULT_TIMEOUT);
    }

    /**
     * Probes each unique remote URL among the given repositories and returns a map from
     * repository {@code location} to {@link ReachabilityStatus}.
     *
     * <p>If {@code offline} is {@code true}, no probes are issued and an empty map is returned —
     * callers should render the "offline" indicator on the report header instead.
     *
     * <p>Repositories whose {@link RepositoryType} is {@link RepositoryType#MAVEN_LOCAL} or
     * {@link RepositoryType#FLAT_DIR} are skipped entirely; their locations never appear in the
     * result map. Non-http(s) schemes are likewise skipped.
     *
     * <p>Probes run in parallel on a dedicated thread pool; results remain keyed by the
     * original {@code location} string regardless of completion order.
     *
     * @param repos all repositories being reported (may contain duplicates by location)
     * @param offline whether the build is in offline mode — when true, no probes run
     * @return map of location to status; empty when offline or no probeable URLs exist
     */
    public Map<String, ReachabilityStatus> check(Collection<ReportRepository> repos, boolean offline) {
        if (offline) {
            return ImmutableMap.of();
        }
        Set<String> uniqueLocations = new LinkedHashSet<>();
        for (ReportRepository r : repos) {
            if (isProbeable(r)) {
                uniqueLocations.add(r.getLocation());
            }
        }
        if (uniqueLocations.isEmpty()) {
            return ImmutableMap.of();
        }
        // HttpClient is not AutoCloseable on Java 17 (this module's release target); rely on GC.
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        int poolSize = Math.min(PROBE_THREAD_POOL_SIZE, uniqueLocations.size());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "repo-reachability-probe");
            t.setDaemon(true);
            return t;
        });
        try {
            Map<String, Future<ReachabilityStatus>> futures = new LinkedHashMap<>();
            for (String location : uniqueLocations) {
                futures.put(location, executor.submit(() -> probe(client, location)));
            }
            ImmutableMap.Builder<String, ReachabilityStatus> result = ImmutableMap.builder();
            // Wall-clock cap per future: timeout + small grace so an individual probe cannot
            // block the entire report indefinitely on a pathological runtime.
            long perFutureCapMs = timeout.toMillis() + 1_000L;
            for (Map.Entry<String, Future<ReachabilityStatus>> e : futures.entrySet()) {
                ReachabilityStatus status;
                try {
                    status = e.getValue().get(perFutureCapMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOGGER.info("Reachability probe of {} failed: {} - {}",
                        e.getKey(), ie.getClass().getName(), ie.getMessage(), ie);
                    status = ReachabilityStatus.UNREACHABLE;
                } catch (ExecutionException ee) {
                    LOGGER.info("Reachability probe of {} failed: {} - {}",
                        e.getKey(), ee.getClass().getName(), ee.getMessage(), ee);
                    status = ReachabilityStatus.UNREACHABLE;
                } catch (TimeoutException te) {
                    LOGGER.info("Reachability probe of {} failed: {} - {}",
                        e.getKey(), te.getClass().getName(), te.getMessage(), te);
                    e.getValue().cancel(true);
                    status = ReachabilityStatus.UNREACHABLE;
                }
                result.put(e.getKey(), status);
            }
            return result.build();
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean isProbeable(ReportRepository r) {
        RepositoryType type = r.getType();
        if (type == RepositoryType.MAVEN_LOCAL || type == RepositoryType.FLAT_DIR) {
            return false;
        }
        String location = r.getLocation();
        return location.startsWith("http://") || location.startsWith("https://");
    }

    /**
     * Probes a single URL. Issues HEAD first; on any 4xx/5xx response, retries with GET and
     * classifies based on the GET response. Returns {@link ReachabilityStatus#MALFORMED_URL}
     * if the location fails URI parsing, and {@link ReachabilityStatus#UNREACHABLE} when no
     * response can be obtained at all.
     *
     * @param client shared HttpClient configured with this instance's timeout
     * @param location the literal repository location string
     * @return the classified {@link ReachabilityStatus}
     */
    private ReachabilityStatus probe(HttpClient client, String location) {
        URI uri;
        try {
            uri = new URI(location);
        } catch (URISyntaxException e) {
            LOGGER.info("Reachability probe of {} failed: {} - {}",
                location, e.getClass().getName(), e.getMessage(), e);
            return ReachabilityStatus.MALFORMED_URL;
        }
        Optional<Integer> headStatus = issue(client, uri, location, "HEAD");
        if (!headStatus.isPresent()) {
            // Network-level failure on HEAD; no point retrying with GET.
            return ReachabilityStatus.UNREACHABLE;
        }
        int head = headStatus.get();
        if (head >= 400 && head < 600) {
            // Server rejected HEAD — fall back to GET and classify on that response.
            Optional<Integer> getStatus = issue(client, uri, location, "GET");
            if (!getStatus.isPresent()) {
                return ReachabilityStatus.UNREACHABLE;
            }
            return classify(getStatus.get());
        }
        return classify(head);
    }

    private static ReachabilityStatus classify(int status) {
        if (status == 401 || status == 403) {
            return ReachabilityStatus.UNAUTHORIZED;
        }
        if (status >= 200 && status < 400) {
            return ReachabilityStatus.REACHABLE;
        }
        return ReachabilityStatus.UNREACHABLE;
    }

    /**
     * Issues one HTTP request. Returns {@link Optional#empty()} on any failure (network,
     * malformed request, interruption); the resulting status — if any — is returned wrapped.
     *
     * <p>All caught exceptions are logged at {@code info} level on this class's logger so
     * diagnostic details surface without polluting default output. {@code InterruptedException}
     * re-interrupts the current thread.
     */
    private Optional<Integer> issue(HttpClient client, URI uri, String location, String method) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return Optional.of(response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.info("Reachability probe of {} failed: {} - {}",
                location, e.getClass().getName(), e.getMessage(), e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.info("Reachability probe of {} failed: {} - {}",
                location, e.getClass().getName(), e.getMessage(), e);
            return Optional.empty();
        }
    }
}
