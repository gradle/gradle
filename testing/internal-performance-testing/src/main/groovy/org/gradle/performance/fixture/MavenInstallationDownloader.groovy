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

package org.gradle.performance.fixture

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.gradle.internal.UncheckedException
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

@Slf4j
class MavenInstallationDownloader {

    private final static Lock LOCK = new ReentrantLock()
    private final static int NETWORK_TIMEOUT_MS = 60 * 1000 // 60 seconds
    // Lock timeout: worst case is ~2-3 minutes (multiple network timeouts + extraction),
    // but we use 5 minutes as a safety net for edge cases (thread crashes, JVM issues, etc.)
    private final static int LOCK_TIMEOUT_SECONDS = 5 * 60 // 5 minutes
    private final File installsRoot

    MavenInstallationDownloader(File installsRoot) {
        this.installsRoot = installsRoot
    }

    MavenInstallation getMavenInstallation(String mavenVersion) {
        try {
            if (!LOCK.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Unable to acquire lock for Maven installation download after ${LOCK_TIMEOUT_SECONDS} seconds")
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw UncheckedException.throwAsUncheckedException(e)
        }
        try {
            def home = mavenInstallDirectory(installsRoot, mavenVersion)
            if (MavenInstallation.valid(home)) {
                return new MavenInstallation(mavenVersion, home)
            }
            def tempHome = downloadAndExtractMavenBinArchiveWithRetry(mavenVersion)
            home = moveToInstallsRoot(tempHome)
            new MavenInstallation(mavenVersion, home)
        } finally {
            LOCK.unlock()
        }
    }

    private static File downloadAndExtractMavenBinArchiveWithRetry(String mavenVersion) {
        def binArchiveUrls = [
            new URL("https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip")
        ]
        
        // Try to get preferred URL, but don't fail if it times out
        try {
            binArchiveUrls << new URL(fetchPreferredUrl(mavenVersion))
        } catch (Exception e) {
            log.warn "Unable to fetch preferred Maven mirror URL, will use primary repository only", e
        }

        for (int i = 0; i < binArchiveUrls.size(); i++) {
            File mavenBinArchive = downloadMavenBinArchive(mavenVersion, binArchiveUrls[i])

            if (mavenBinArchive) {
                def extracted = extractMavenBinArchive(mavenVersion, mavenBinArchive)
                if (extracted) {
                    return extracted
                }
            }
        }
        throw UncheckedException.throwAsUncheckedException(new IOException("Unable to download Maven binary distribution from any of the repositories"), true);
    }

    private static File downloadMavenBinArchive(String mavenVersion, URL binArchiveUrl) {
        try {
            log.info "Attempting to downloading Maven binary distribution from '$binArchiveUrl'"
            return downloadBinArchive(mavenVersion, binArchiveUrl)
        } catch (Exception e) {
            log.warn "Unable to download Maven distribution from '$binArchiveUrl'", e
        }
    }

    private static File mavenInstallDirectory(File parent, String mavenVersion) {
        new File(parent, "apache-maven-$mavenVersion")
    }

    private static String fetchPreferredUrl(String mavenVersion) {
        // Use ASF preferred mirror resolution
        def url = "https://www.apache.org/dyn/closer.cgi/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip?as_json=1"
        def urlConnection = new URL(url).openConnection()
        urlConnection.setConnectTimeout(NETWORK_TIMEOUT_MS)
        urlConnection.setReadTimeout(NETWORK_TIMEOUT_MS)
        def parsed
        urlConnection.getInputStream().withCloseable { inputStream ->
            parsed = new JsonSlurper().parse(inputStream)
        }
        parsed.preferred + parsed.path_info
    }

    private static File downloadBinArchive(String mavenVersion, URL binArchiveUrl) {
        def target = File.createTempFile("maven-bin-$mavenVersion-", ".zip")
        def urlConnection = binArchiveUrl.openConnection()
        urlConnection.setConnectTimeout(NETWORK_TIMEOUT_MS)
        urlConnection.setReadTimeout(NETWORK_TIMEOUT_MS)
        target.withOutputStream { out ->
            urlConnection.getInputStream().withCloseable { input ->
                out << input
            }
        }
        target
    }

    private static File extractMavenBinArchive(String mavenVersion, File binArchive) {
        try {
            return extractBinArchive(mavenVersion, binArchive)
        } catch (Exception e) {
            log.warn "Unable to unpack Maven distribution '$binArchive'", e
            return null
        }
    }

    private static File extractBinArchive(String mavenVersion, File binArchive) {
        def target = File.createTempDir("maven-install-$mavenVersion-", "")
        new TestFile(binArchive).unzipTo(target)
        mavenInstallDirectory(target, mavenVersion)
    }

    private File moveToInstallsRoot(File tmpHome) {
        FileUtils.moveDirectoryToDirectory(tmpHome, installsRoot, true)
        def home = new File(installsRoot, tmpHome.getName())
        if (!OperatingSystem.current().isWindows()) {
            // We do this after the move because that "move" can fallback to a Java copy
            // that does not preserve permissions when java.io.tmpdir sits in a different filesystem).
            TestFile executable = new TestFile(MavenInstallation.findMvnExecutable(home))
            executable.permissions = "rwxr--r--"
        }
        home
    }
}
