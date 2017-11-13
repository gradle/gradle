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
import org.gradle.api.UncheckedIOException
import org.gradle.internal.os.OperatingSystem

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

@Slf4j
class MavenInstallationDownloader {

    private final static Lock LOCK = new ReentrantLock()
    private final File installsRoot

    public MavenInstallationDownloader(File installsRoot) {
        this.installsRoot = installsRoot
    }

    public MavenInstallation getMavenInstallation(String mavenVersion) {
        LOCK.lock()
        try {
            def home = mavenInstallDirectory(installsRoot, mavenVersion)
            if (MavenInstallation.valid(home)) {
                return new MavenInstallation(mavenVersion, home)
            }
            def binArchive = downloadMavenBinArchiveWithRetry(mavenVersion)
            def tempHome = extractBinArchive(mavenVersion, binArchive)
            home = moveToInstallsRoot(tempHome)
            new MavenInstallation(mavenVersion, home)
        } finally {
            LOCK.unlock()
        }
    }

    private static File downloadMavenBinArchiveWithRetry(String mavenVersion) {
        def binArchiveUrls = [
            new URL("https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip"),
            new URL(fetchPreferredUrl(mavenVersion))
        ]

        for (int i = 0; i < binArchiveUrls.size(); i++) {
            File mavenBinArchive = downloadMavenBinArchive(mavenVersion, binArchiveUrls[i])

            if (mavenBinArchive) {
                return mavenBinArchive
            } else if (!mavenBinArchive && i == (binArchiveUrls.size() - 1)) {
                throw new UncheckedIOException("Unable to download Maven binary distribution from any of the repositories")
            }
        }
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
        def url = "http://www.apache.org/dyn/closer.cgi/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip?as_json=1"
        def parsed = new JsonSlurper().parse(new URL(url))
        parsed.preferred + parsed.path_info
    }

    private static File downloadBinArchive(String mavenVersion, URL binArchiveUrl) {
        def target = File.createTempFile("maven-bin-$mavenVersion-", ".zip")
        target.withOutputStream { out ->
            out << binArchiveUrl.openStream()
        }
        target
    }

    private static File extractBinArchive(String mavenVersion, File binArchive) {
        def target = File.createTempDir("maven-install-$mavenVersion-", "")
        def ant = new AntBuilder()
        ant.mkdir(dir: target)
        ant.unzip(src: binArchive, dest: target)
        mavenInstallDirectory(target, mavenVersion)
    }

    private File moveToInstallsRoot(File tmpHome) {
        FileUtils.moveDirectoryToDirectory(tmpHome, installsRoot, true)
        def home = new File(installsRoot, tmpHome.getName())
        if (!OperatingSystem.current().isWindows()) {
            // We do this after the move because that "move" can fallback to a Java copy
            // that does not preserve permissions when java.io.tmpdir sits in a different filesystem).
            new AntBuilder().chmod(file: MavenInstallation.findMvnExecutable(home), perm: "+x")
        }
        home
    }
}
