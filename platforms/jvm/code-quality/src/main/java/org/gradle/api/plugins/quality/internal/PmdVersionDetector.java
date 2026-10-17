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

package org.gradle.api.plugins.quality.internal;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Determines the PMD version from the Maven metadata that every PMD release ships in its core jar.
 */
public final class PmdVersionDetector {

    private static final List<String> POM_PROPERTIES = ImmutableList.of(
        // PMD 5.2.0 and later
        "META-INF/maven/net.sourceforge.pmd/pmd-core/pom.properties",
        // PMD 5.0.0 to 5.1.x
        "META-INF/maven/net.sourceforge.pmd/pmd/pom.properties",
        // PMD 4.x
        "META-INF/maven/pmd/pmd/pom.properties"
    );

    private PmdVersionDetector() {
    }

    /**
     * Returns the version of the first PMD core jar or directory on the classpath, or {@code null} if there is none.
     */
    @Nullable
    public static String detect(Iterable<File> classpath) {
        for (File entry : classpath) {
            String version = versionOf(entry);
            if (version != null) {
                return version;
            }
        }
        return null;
    }

    @Nullable
    private static String versionOf(File entry) {
        try {
            if (entry.isDirectory()) {
                for (String path : POM_PROPERTIES) {
                    File file = new File(entry, path);
                    if (file.isFile()) {
                        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                            return readVersion(inputStream);
                        }
                    }
                }
            } else if (entry.isFile()) {
                try (ZipFile zipFile = new ZipFile(entry)) {
                    for (String path : POM_PROPERTIES) {
                        ZipEntry zipEntry = zipFile.getEntry(path);
                        if (zipEntry != null) {
                            try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                                return readVersion(inputStream);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Not a readable jar or directory, cannot contain PMD
        }
        return null;
    }

    @Nullable
    private static String readVersion(InputStream inputStream) throws IOException {
        Properties properties = new Properties();
        properties.load(inputStream);
        return properties.getProperty("version");
    }
}
