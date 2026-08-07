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

package gradlebuild.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks HTTP/HTTPS links in .adoc and .md files for reachability.
 *
 * <p>Runs a HEAD request per unique URL (with GET fallback when HEAD is rejected).
 * Success = HTTP 2xx or 3xx. Anything else, or a network error, is reported.
 * Fragments (#anchor) are stripped before checking.
 *
 * <p>This task hits the network. It is intended for CI, not for `check`. If a link is broken
 * only because a remote server is briefly down, the task will fail — the report file will show
 * which URLs failed and where they appear.
 */
@CacheableTask
public abstract class FindBrokenExternalLinks extends DefaultTask {

    private static final int TIMEOUT_MS = 5_000;
    private static final int THREAD_POOL_SIZE = 16;
    private static final String USER_AGENT = "gradle-docs-link-checker/1.0";

    // Matches https:// or http:// URLs
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\[\\]<>\"'`|)]+");

    // Host suffixes to skip
    private static final Set<String> SKIP_HOST_SUFFIXES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        // Acceptable examples
        "example.com",
        "example.org",
        "example.net",
        "localhost",
        "127.0.0.1",
        "0.0.0.0",
        "my.key.server.com",
        "my.server.com",
        "your.server.com",
        "your.ivy.repo",
        "your.maven.repo",
        "your.secure.repo",
        "schema.gradle.org",
        // Rate limiting
        "mvnrepository.com",
        "repo.maven.apache.org",
        "bugs.openjdk.org",
        "gradle-community.slack.com",
        "bugs.java.com",
        // Frequently unreachable but still the canonical destination — do not rewrite these links.
        // groovy-lang.org is Apache Groovy's canonical docs domain (referenced from groovy.apache.org itself).
        "groovy-lang.org",
        "docs.groovy-lang.org"
    )));

    // URL substrings that mark unreliable-to-check paths
    private static final List<String> SKIP_URL_SUBSTRINGS = Collections.unmodifiableList(Arrays.asList(
        "/edit/",               // GitHub edit URLs redirect to login
        "localhost",            // any URL containing localhost
        "gradle.com/s/link"     // known placeholder used in tutorials
    ));

    // URL prefixes to skip
    private static final List<String> SKIP_URL_PREFIXES = Collections.unmodifiableList(Arrays.asList(
        "https://stackoverflow.com/questions/tagged/",  // SO rate-limits/blocks bot HEAD requests on tag pages
        "https://marketplace.visualstudio.com/", // Legit
        "https://keys.openpgp.org/vks/v1/by-fingerprint/" // Example
    ));

    // Host suffixes that must never appear in docs
    private static final Set<String> BLOCKED_HOST_SUFFIXES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        // Ad-hoc company placeholders we don't want to allow
        "mycompany.com",
        "my.company.com",
        "company.com",
        "some-company.com",
        "foo.com",
        // Real domains commonly mistaken for placeholders
        "test.com",
        "domain.com",
        "website.com",
        "mydomain.com",
        "mysite.com"
    )));

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDocumentationRoot();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void check() {
        CollectResult collected = collectUrls();
        getLogger().lifecycle("Checking {} unique external URLs...", collected.urlsToCheck.size());

        Map<String, String> failures = checkAll(collected.urlsToCheck.keySet());
        // Blocked URLs are reported as failures without any HTTP request.
        for (String blocked : collected.blockedUrls.keySet()) {
            failures.put(blocked, "blocked placeholder host — use example.com/example.org instead");
        }

        Map<String, List<Occurrence>> allUrls = new TreeMap<>(collected.urlsToCheck);
        allUrls.putAll(collected.blockedUrls);
        writeReport(allUrls, failures);
    }

    private CollectResult collectUrls() {
        CollectResult result = new CollectResult();
        File root = getDocumentationRoot().get().getAsFile();
        try (Stream<Path> stream = Files.walk(root.toPath())) {
            stream.filter(Files::isRegularFile)
                .filter(FindBrokenExternalLinks::isDocFile)
                .forEach(path -> extractUrls(path, result));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        return result;
    }

    private static boolean isDocFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".adoc") || name.endsWith(".md");
    }

    private void extractUrls(Path file, CollectResult result) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher m = URL_PATTERN.matcher(line);
                while (m.find()) {
                    String url = normalize(m.group());
                    if (url == null) {
                        continue;
                    }
                    Occurrence occ = new Occurrence(file.toFile(), i + 1);
                    if (isBlocked(url)) {
                        result.blockedUrls.computeIfAbsent(url, k -> new ArrayList<>()).add(occ);
                    } else if (!shouldSkip(url)) {
                        result.urlsToCheck.computeIfAbsent(url, k -> new ArrayList<>()).add(occ);
                    }
                }
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static boolean isBlocked(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null) {
                String lower = host.toLowerCase();
                for (String suffix : BLOCKED_HOST_SUFFIXES) {
                    if (lower.equals(suffix) || lower.endsWith("." + suffix)) {
                        return true;
                    }
                }
            }
        } catch (URISyntaxException e) {
            // Malformed URIs are handled by shouldSkip().
        }
        return false;
    }

    private static String normalize(String url) {
        // Trim trailing punctuation
        while (!url.isEmpty()) {
            char last = url.charAt(url.length() - 1);
            if (last == '.' || last == ',' || last == ';' || last == ':'
                || last == ')' || last == ']' || last == '}' || last == '>'
                || last == '"' || last == '\'' || last == '`') {
                url = url.substring(0, url.length() - 1);
            } else {
                break;
            }
        }
        // Strip fragment
        int hash = url.indexOf('#');
        if (hash >= 0) {
            url = url.substring(0, hash);
        }
        // Strip AsciiDoc link text
        int openBracket = url.indexOf('[');
        if (openBracket >= 0) {
            url = url.substring(0, openBracket);
        }
        return url.isEmpty() ? null : url;
    }

    private static boolean shouldSkip(String url) {
        for (String prefix : SKIP_URL_PREFIXES) {
            if (url.startsWith(prefix)) {
                return true;
            }
        }
        for (String substr : SKIP_URL_SUBSTRINGS) {
            if (url.contains(substr)) {
                return true;
            }
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null) {
                String lower = host.toLowerCase();
                for (String suffix : SKIP_HOST_SUFFIXES) {
                    if (lower.equals(suffix) || lower.endsWith("." + suffix)) {
                        return true;
                    }
                }
            }
        } catch (URISyntaxException e) {
            return true;
        }
        return false;
    }

    private Map<String, String> checkAll(Set<String> urls) {
        Map<String, String> failures = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (String url : urls) {
                futures.add(CompletableFuture.runAsync(() -> {
                    String failure = checkOne(url);
                    if (failure != null) {
                        failures.put(url, failure);
                    }
                }, executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
        return failures;
    }

    // Returns null on success, an error message on failure.
    private String checkOne(String url) {
        try {
            int code = request(url, "HEAD");
            if (code == 405 || code == 403 || code == 400 || code == 501) {
                // HEAD-hostile servers — retry with GET.
                code = request(url, "GET");
            }
            if (code >= 200 && code < 400) {
                return null;
            }
            return "HTTP " + code;
        } catch (IOException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private int request(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private void writeReport(Map<String, List<Occurrence>> urls, Map<String, String> failures) {
        File reportFile = getReportFile().get().getAsFile();
        try (PrintWriter fw = new PrintWriter(new FileWriter(reportFile))) {
            fw.println("# External link check");
            fw.println("# Scanned " + urls.size() + " unique URLs across .adoc and .md files.");
            fw.println("# Skipped host suffixes: " + SKIP_HOST_SUFFIXES);
            fw.println("# Skipped URL substrings: " + SKIP_URL_SUBSTRINGS);
            fw.println("# Skipped URL prefixes: " + SKIP_URL_PREFIXES);
            fw.println("# Blocked host suffixes (must not appear in docs): " + BLOCKED_HOST_SUFFIXES);
            fw.println();
            if (failures.isEmpty()) {
                fw.println("All clear!");
                return;
            }
            fw.println("Found " + failures.size() + " broken external links:");
            fw.println();
            for (Map.Entry<String, String> failure : new TreeMap<>(failures).entrySet()) {
                String url = failure.getKey();
                fw.println(url);
                fw.println("  reason: " + failure.getValue());
                for (Occurrence occ : urls.get(url)) {
                    fw.println("  at " + occ.file.getName() + ":" + occ.lineNumber);
                }
                fw.println();
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        if (!failures.isEmpty()) {
            String message = "Documentation assertion failed: found " + failures.size()
                + " broken external links. See "
                + new org.gradle.internal.logging.ConsoleRenderer().asClickableFileUrl(reportFile);
            getLogger().error(message);
            throw new GradleException(message);
        }
    }

    private static final class Occurrence {
        final File file;
        final int lineNumber;

        Occurrence(File file, int lineNumber) {
            this.file = file;
            this.lineNumber = lineNumber;
        }
    }

    private static final class CollectResult {
        final Map<String, List<Occurrence>> urlsToCheck = new TreeMap<>();
        final Map<String, List<Occurrence>> blockedUrls = new TreeMap<>();
    }
}
