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

package org.gradle.integtests.fixtures.versions;

import org.gradle.internal.UncheckedException;
import org.gradle.util.GradleVersion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Kotlin version embedded in each Gradle version, and the Kotlin language version that Gradle's Kotlin DSL targets.
 * <p>
 * Reads {@code gradle/dependency-management/embedded-kotlin-versions.csv}, which is also the source of the Kotlin table in the compatibility matrix.
 */
public class EmbeddedKotlinVersions {

    private static final String RESOURCE_NAME = "embedded-kotlin-versions.csv";

    private final List<Entry> entries;

    public EmbeddedKotlinVersions() {
        this.entries = Collections.unmodifiableList(load());
    }

    public String kotlinLanguageVersionOf(GradleVersion gradleVersion) {
        return entryFor(gradleVersion).getKotlinLanguageVersion();
    }

    public Entry entryFor(GradleVersion gradleVersion) {
        GradleVersion baseVersion = gradleVersion.getBaseVersion();
        Entry found = null;
        for (Entry entry : entries) {
            if (entry.getMinimumGradleVersion().compareTo(baseVersion) <= 0) {
                found = entry;
            }
        }
        if (found == null) {
            throw new IllegalArgumentException("No embedded Kotlin version is known for Gradle " + gradleVersion.getVersion() + ".");
        }
        return found;
    }

    private static List<Entry> load() {
        URL resource = EmbeddedKotlinVersions.class.getClassLoader().getResource(RESOURCE_NAME);
        if (resource == null) {
            throw new IllegalStateException("The resource '" + RESOURCE_NAME + "' was not found. Most likely, you haven't run the 'copyTestedVersionsInfo' task.");
        }
        List<Entry> entries = new ArrayList<Entry>();
        try (InputStream stream = resource.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalStateException("'" + RESOURCE_NAME + "' is empty.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] cells = line.split(",");
                if (cells.length != 3) {
                    throw new IllegalStateException("Expected 3 columns in '" + RESOURCE_NAME + "', but got: " + line);
                }
                entries.add(new Entry(cells[0].trim(), GradleVersion.version(cells[1].trim()), cells[2].trim()));
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i - 1).getMinimumGradleVersion().compareTo(entries.get(i).getMinimumGradleVersion()) >= 0) {
                throw new IllegalStateException("'" + RESOURCE_NAME + "' must be ordered by Gradle version, but " + entries.get(i).getMinimumGradleVersion().getVersion() + " comes after " + entries.get(i - 1).getMinimumGradleVersion().getVersion() + ".");
            }
        }
        return entries;
    }

    public static final class Entry {
        private final String embeddedKotlinVersion;
        private final GradleVersion minimumGradleVersion;
        private final String kotlinLanguageVersion;

        Entry(String embeddedKotlinVersion, GradleVersion minimumGradleVersion, String kotlinLanguageVersion) {
            this.embeddedKotlinVersion = embeddedKotlinVersion;
            this.minimumGradleVersion = minimumGradleVersion;
            this.kotlinLanguageVersion = kotlinLanguageVersion;
        }

        public String getEmbeddedKotlinVersion() {
            return embeddedKotlinVersion;
        }

        public GradleVersion getMinimumGradleVersion() {
            return minimumGradleVersion;
        }

        public String getKotlinLanguageVersion() {
            return kotlinLanguageVersion;
        }
    }
}
