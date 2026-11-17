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

package gradlebuild.cleanup.services;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * NOTICE: this class is invoked via java command line, so we must NOT DEPEND ON ANY 3RD-PARTY LIBRARIES except JDK 11.
 *
 * Decides whether a failed build should be retried, and requests the retry with a TeamCity service message.
 * Merge queue builds are always retried without asking gradle-bot. Other builds are retried only if gradle-bot recommends it and the mode is "retry".
 * It never fails: if gradle-bot can't be reached, it prints a warning and doesn't request a retry.
 *
 * Usage: java build-logic/cleanup/src/main/java/gradlebuild/cleanup/services/RetryDecision.java branch mode endpoint buildId password
 */
public class RetryDecision {
    private static final String REQUEST_RETRY = "##teamcity[setParameter name='retry.requested' value='true']";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    public static void main(String[] args) {
        String branch = args[0];
        String mode = args[1];
        String endpoint = args[2];
        String buildId = args[3];
        String password = args[4];

        if (branch.startsWith("gh-readonly-queue/")) {
            System.out.println(REQUEST_RETRY);
            return;
        }
        if ("off".equals(mode)) {
            return;
        }
        try {
            System.out.print(ask(endpoint, buildId, mode, password));
        } catch (Exception e) {
            System.out.println("##teamcity[message text='" + escape("Retry decision is unavailable: " + e) + "' status='WARNING']");
        }
    }

    private static String ask(String endpoint, String buildId, String mode, String password) throws IOException, InterruptedException {
        String credentials = Base64.getEncoder().encodeToString(("teamcity:" + password).getBytes(UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "?buildId=" + encode(buildId) + "&mode=" + encode(mode)))
            .timeout(TIMEOUT)
            .header("Authorization", "Basic " + credentials)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString());
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
