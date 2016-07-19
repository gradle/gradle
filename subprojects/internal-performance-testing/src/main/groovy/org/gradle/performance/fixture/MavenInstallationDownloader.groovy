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
import org.apache.commons.io.FileUtils
import org.gradle.internal.os.OperatingSystem

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

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
            def binArchive
            try {
                binArchive = downloadMavenBinFromRandomMirror(mavenVersion)
            } catch (IOException ignored) {
                // Retry getting maven once in case we get a bad mirror
                binArchive = downloadMavenBinFromRandomMirror(mavenVersion)
            }
            def tempHome = extractBinArchive(mavenVersion, binArchive)
            home = moveToInstallsRoot(tempHome)
            new MavenInstallation(mavenVersion, home)
        } finally {
            LOCK.unlock()
        }
    }

    private static File mavenInstallDirectory(File parent, String mavenVersion) {
        new File(parent, "apache-maven-$mavenVersion")
    }

    private static String fetchPreferredUrl(String mavenVersion) {
        // Use ASF preferred mirror resolution
        def url = "http://www.apache.org/dyn/closer.cgi/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.tar.gz?as_json=1"
        def parsed = new JsonSlurper().parse(new URL(url))
        parsed.preferred + parsed.path_info
    }

    private static File downloadBinArchive(String mavenVersion, String binArchiveUrl) {
        def target = File.createTempFile("maven-bin-$mavenVersion-", ".tar.gz")
        target.withOutputStream { out ->
            out << new URL(binArchiveUrl).openStream()
        }
        target
    }

    private static File extractBinArchive(String mavenVersion, File binArchive) {
        def target = File.createTempDir("maven-install-$mavenVersion-", "")
        def ant = new AntBuilder()
        ant.mkdir(dir: target)
        ant.untar(src: binArchive, dest: target, compression: "gzip")
        mavenInstallDirectory(target, mavenVersion)
    }

    private static File downloadMavenBinFromRandomMirror(String mavenVersion) {
        def preferredUrl = fetchPreferredUrl(mavenVersion)
        downloadBinArchive(mavenVersion, preferredUrl)
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
