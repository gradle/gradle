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

class ReleasedVersions {
    private static final Logger LOGGER = Logging.getLogger(ReleasedVersions.class)
    private static final int MILLIS_PER_DAY = 24 * 60 * 60 * 1000

    private lowestInterestingVersion = GradleVersion.version("0.8")
    private def versions
    private def snapshots

    File destFile
    String url = "https://services.gradle.org/versions/all"
    boolean offline

    void prepare() {
        download()
        versions = calculateVersions()
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
        if (destFile.isFile() && destFile.lastModified() > System.currentTimeMillis() - MILLIS_PER_DAY) {
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
        }

        destFile.parentFile.mkdirs()
        destFile.text = json

        LOGGER.info "Saved released versions information in: $destFile"
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

    List<String> getAllSnapshots() {
        return snapshots*.version*.version
    }

    List<Map<String, ?>> calculateVersions() {
        def versions = new groovy.json.JsonSlurper().parseText(destFile.text).findAll {
            (it.activeRc == true || it.rcFor == "") && it.broken == false && it.snapshot == false
        }.collect {
            it.version = GradleVersion.version(it.version)
            it
        }.findAll {
            it.version >= lowestInterestingVersion
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
