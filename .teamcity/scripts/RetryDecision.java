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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TeamCity helper script that decides whether a failed build should be retried, and requests the retry with a TeamCity service message.
 * Merge queue builds are always retried without asking gradle-bot. Other builds are retried only if gradle-bot recommends it and the mode is "retry".
 * It never fails and finishes within the timeout: if gradle-bot can't answer in time, it prints a warning and doesn't request a retry.
 *
 * Usage (Java 11+ single-file source execution):
 *   java .teamcity/scripts/RetryDecision.java branch mode endpoint buildId password timeoutSeconds
 */
public class RetryDecision {
    private static final String REQUEST_RETRY = "##teamcity[setParameter name='retry.requested' value='true']";
    private static final Duration EXIT_MARGIN = Duration.ofSeconds(1);

    public static void main(String[] args) {
        String branch = args[0];
        String mode = args[1];
        String endpoint = args[2];
        String buildId = args[3];
        String password = args[4];
        String timeoutSeconds = args[5];

        if (branch.startsWith("gh-readonly-queue/")) {
            System.out.println(REQUEST_RETRY);
            return;
        }
        if ("off".equals(mode)) {
            return;
        }
        try {
            Duration stepTimeout = Duration.ofSeconds(Long.parseLong(timeoutSeconds));
            System.out.print(ask(endpoint, buildId, mode, password, timeoutSeconds, remainingTime(stepTimeout)));
        } catch (ExecutionException e) {
            warn(e.getCause());
        } catch (Exception e) {
            warn(e);
        }
        System.exit(0);
    }

    private static Duration remainingTime(Duration stepTimeout) {
        Duration elapsed = ProcessHandle.current().info().startInstant()
            .map(start -> Duration.between(start, Instant.now()))
            .orElse(Duration.ZERO);
        return stepTimeout.minus(elapsed).minus(EXIT_MARGIN);
    }

    private static void warn(Throwable failure) {
        System.out.println("##teamcity[message text='" + escape("Retry decision is unavailable: " + failure) + "' status='WARNING']");
    }

    private static String ask(String endpoint, String buildId, String mode, String password, String timeoutSeconds, Duration timeout) throws Exception {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new TimeoutException("no time left before the " + timeoutSeconds + "s limit");
        }
        String credentials = Base64.getEncoder().encodeToString(("teamcity:" + password).getBytes(UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "?buildId=" + encode(buildId) + "&mode=" + encode(mode) + "&timeoutSeconds=" + encode(timeoutSeconds)))
            .timeout(timeout)
            .header("Authorization", "Basic " + credentials)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build()
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (response.statusCode() != 200) {
            throw new IOException("gradle-bot returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, UTF_8);
    }

    private static String escape(String value) {
        return value
            .replace("|", "||")
            .replace("'", "|'")
            .replace("\n", "|n")
            .replace("\r", "|r")
            .replace("[", "|[")
            .replace("]", "|]");
    }
}
