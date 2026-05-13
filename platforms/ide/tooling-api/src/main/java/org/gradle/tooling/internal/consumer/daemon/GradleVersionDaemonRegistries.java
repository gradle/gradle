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
package org.gradle.tooling.internal.consumer.daemon;

import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates daemon registry files under a Gradle user home, one per Gradle version.
 *
 * <p>Layout: {@code <gradleUserHome>/daemon/<gradle-version>/registry.bin}.
 */
final class GradleVersionDaemonRegistries {

    private final File daemonBaseDir;

    GradleVersionDaemonRegistries(File gradleUserHome) {
        this.daemonBaseDir = new File(gradleUserHome, "daemon");
    }

    /**
     * Returns one entry per version directory that contains a {@code registry.bin}.
     */
    List<Entry> findAll() {
        List<Entry> entries = new ArrayList<>();
        File[] versionDirs = daemonBaseDir.listFiles();
        if (versionDirs != null) {
            for (File versionDir : versionDirs) {
                if (!versionDir.isDirectory()) {
                    continue;
                }
                String version = versionDir.getName();
                if (!isValidVersion(version)) {
                    continue;
                }
                File registry = new File(versionDir, "registry.bin");
                if (registry.isFile()) {
                    entries.add(new Entry(version, registry));
                }
            }
        }
        return entries;
    }

    Entry findForVersion(String gradleVersion) {
        File versionDir = new File(daemonBaseDir, gradleVersion);
        File registry = new File(versionDir, "registry.bin");
        return registry.isFile() ? new Entry(gradleVersion, registry) : null;
    }

    private static boolean isValidVersion(String name) {
        try {
            GradleVersion.version(name);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static final class Entry {
        final String gradleVersion;
        final File registryFile;

        Entry(String gradleVersion, File registryFile) {
            this.gradleVersion = gradleVersion;
            this.registryFile = registryFile;
        }
    }
}
