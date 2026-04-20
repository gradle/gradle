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
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Issues lightweight HEAD (or GET fallback) probes to unique remote repository URLs to classify
 * their current reachability. Intended for diagnostic purposes only — not for build resolution.
 *
 * <p>Credentials are not sent; any URL gated behind HTTP authentication will be reported as
 * {@link ReachabilityStatus#UNAUTHORIZED}.
 */
@NullMarked
public final class RepositoryReachabilityChecker {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final Duration timeout;

    public RepositoryReachabilityChecker() {
        this(DEFAULT_TIMEOUT);
    }

    public RepositoryReachabilityChecker(Duration timeout) {
        this.timeout = timeout;
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
     * result map.
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
        ImmutableMap.Builder<String, ReachabilityStatus> result = ImmutableMap.builder();
        // HttpClient is not AutoCloseable on Java 17 (this module's release target); rely on GC.
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        for (String location : uniqueLocations) {
            result.put(location, probe(client, location));
        }
        return result.build();
    }

    private static boolean isProbeable(ReportRepository r) {
        RepositoryType type = r.getType();
        if (type == RepositoryType.MAVEN_LOCAL || type == RepositoryType.FLAT_DIR) {
            return false;
        }
        String location = r.getLocation();
        return location.startsWith("http://") || location.startsWith("https://");
    }

    private ReachabilityStatus probe(HttpClient client, String location) {
        URI uri;
        try {
            uri = new URI(location);
        } catch (URISyntaxException e) {
            return ReachabilityStatus.UNREACHABLE;
        }
        Integer headStatus = issue(client, uri, "HEAD");
        if (headStatus == null) {
            // Network-level failure on HEAD; don't bother retrying with GET.
            return ReachabilityStatus.UNREACHABLE;
        }
        if (headStatus == 405) {
            // Server rejected HEAD — fall back to GET.
            Integer getStatus = issue(client, uri, "GET");
            if (getStatus == null) {
                return ReachabilityStatus.UNREACHABLE;
            }
            return classify(getStatus);
        }
        return classify(headStatus);
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
     * Issues one HTTP request. Returns the response status code on success, or {@code null}
     * on a network-level failure (DNS, connect refused, timeout, malformed request, etc.).
     */
    private @Nullable Integer issue(HttpClient client, URI uri, String method) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        } catch (IllegalArgumentException e) {
            return null;
        }
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            return null;
        }
    }
}
