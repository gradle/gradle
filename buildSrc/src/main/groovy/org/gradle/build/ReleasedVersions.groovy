/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.build

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.util.GradleVersion

import java.util.concurrent.TimeUnit

class ReleasedVersions {
    private static final Logger LOGGER = Logging.getLogger(ReleasedVersions.class)

    private lowestInterestingVersion = GradleVersion.version("0.8")
    private lowestTestedVersion = GradleVersion.version("1.0")
    private currentVersion = GradleVersion.current()
    private def versions
    private def testedVersions
    private def snapshots

    File destFile
    String url = "https://services.gradle.org/versions/all"
    boolean offline
    boolean alwaysDownload

    void prepare() {
        download()
        versions = calculateVersions()
        testedVersions = calculateVersions(lowestTestedVersion)
        snapshots = calculateSnapshots()
    }

    private void download() {
        if (offline) {
            if (!destFile.isFile()) {
                throw new RuntimeException("The versions info file (${destFile.name}) does not exist from a previous build and cannot be downloaded (due to --offline switch).\n"
                    + "After running 'clean', build must be executed once online before going offline")
            }
            LOGGER.warn("Versions information will not be downloaded because --offline switch is used.\n"
                + "Without the version information certain integration tests may fail or use outdated version details.")
            return
        }
        if (!alwaysDownload && destFile.isFile() && destFile.lastModified() > System.currentTimeMillis() - TimeUnit.HOURS.toMillis(4)) {
            LOGGER.info("Don't download released versions from $url as the output file already exists and is not out-of-date.")
            return
        }
        LOGGER.lifecycle "Downloading the released versions from: $url"

        def json
        try {
            json = new URL(url).text
        } catch (UnknownHostException e) {
            throw new GradleException("Unable to acquire versions info. I've tried this url: '$url'.\n"
                + "If you don't have the network connection please run with '--offline' or exclude this task from execution via '-x'."
                , e)
        } catch (IOException e) {
            traceRoute(new URL(url))
            throw e
        }
        destFile.parentFile.mkdirs()
        destFile.text = json

        LOGGER.info "Saved released versions information in: $destFile"
    }

    static void traceRoute(URL location) {
        LOGGER.lifecycle("Beginning traceroute to ${location.getHost()}")
        def standardOut = new StringBuffer(), standardErr = new StringBuffer()
        String command
        if (org.gradle.internal.os.OperatingSystem.current().windows) {
            command = "tracert ${location.getHost()}"
        } else {
            command = "traceroute ${location.getHost()}"
        }
        def proc = command.execute()
        proc.consumeProcessOutput(standardOut, standardErr)
        proc.waitFor()
        LOGGER.lifecycle("Route trace to ${location.getHost()}")
        LOGGER.lifecycle("""out:
$standardOut
err:
$standardErr""")
    }

    String getMostRecentFinalRelease() {
        return versions.findAll { it.rcFor == "" }.first().version.version
    }

    String getMostRecentSnapshot() {
        return snapshots.first().version.version
    }

    List<String> getAllVersions() {
        return versions*.version*.version
    }

    List<String> getTestedVersions() {
        return testedVersions*.version*.version
    }

    List<String> getAllSnapshots() {
        return snapshots*.version*.version
    }

    private static boolean isActiveVersion(def version) {
        // Ignore broken or snapshot versions
        if (version.broken == true || version.snapshot == true) {
            return false
        }
        // Ignore milestone releases
        if (version.version.contains('milestone')) {
            return false;
        }
        // Include only active RCs
        if (version.rcFor != "") {
            return version.activeRc
        }
        // Include all other versions
        return true
    }

    List<Map<String, ?>> calculateVersions(def startingAt = lowestInterestingVersion) {
        def versions = new groovy.json.JsonSlurper().parseText(destFile.text).findAll {
            isActiveVersion(it)
        }.collect {
            it.version = GradleVersion.version(it.version)
            it
        }.findAll {
            it.version >= startingAt && it.version <= currentVersion
        }.sort {
            it.version
        }.reverse()

        if (versions.size() < 10) {
            throw new IllegalStateException("Too few previous releases found in ${destFile}: " + versions)
        }

        return versions
    }

    List<Map<String, ?>> calculateSnapshots() {
        def snapshots = new groovy.json.JsonSlurper().parseText(destFile.text).findAll {
            (it.snapshot == true || it.nightly == true) && it.broken == false
        }.collect {
            it.version = GradleVersion.version(it.version)
            it
        }.findAll {
            it.version >= lowestInterestingVersion
        }.sort {
            it.version
        }.reverse()

        if (snapshots.size() < 1) {
            throw new IllegalStateException("Too few snapshots found in ${destFile}: " + versions)
        }

        return snapshots
    }
}
