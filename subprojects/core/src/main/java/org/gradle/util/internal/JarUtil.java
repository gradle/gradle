/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.util.internal;

import org.apache.commons.io.IOUtils;
import org.gradle.internal.IoActions;

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.OptionalInt;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarUtil {
    // We cannot use Attributes.Name.MULTI_RELEASE as it is only available since Java 9;
    public static final String MULTI_RELEASE_ATTRIBUTE = "Multi-Release";
    // Pattern for JAR entries in the versioned directories.
    // See https://docs.oracle.com/en/java/javase/20/docs/specs/jar/jar.html#multi-release-jar-files
    private static final Pattern VERSIONED_JAR_ENTRY_PATH = Pattern.compile("^META-INF/versions/(\\d+)/.+$");

    public static boolean extractZipEntry(File jarFile, String entryName, File extractToFile) throws IOException {
        boolean entryExtracted = false;

        ZipInputStream zipStream = null;
        BufferedOutputStream extractTargetStream = null;
        try {
            zipStream = new ZipInputStream(new FileInputStream(jarFile));
            extractTargetStream = new BufferedOutputStream(new FileOutputStream(extractToFile));

            boolean classFileExtracted = false;
            boolean zipStreamEndReached = false;
            while (!classFileExtracted && !zipStreamEndReached) {
                final ZipEntry candidateZipEntry = zipStream.getNextEntry();

                if (candidateZipEntry == null) {
                    zipStreamEndReached = true;
                } else {
                    if (candidateZipEntry.getName().equals(entryName)) {
                        IOUtils.copy(zipStream, extractTargetStream);
                        classFileExtracted = true;
                        entryExtracted = true;
                    }
                }
            }
        } finally {
            IoActions.closeQuietly(zipStream);
            IoActions.closeQuietly(extractTargetStream);
        }

        return entryExtracted;
    }

    /**
     * Checks if the {@code jarEntryName} is the name of the JAR manifest entry.
     *
     * @param jarEntryName the name of the entry
     * @return {@code true} if the entry is the JAR manifest
     */
    public static boolean isManifestName(String jarEntryName) {
        return jarEntryName.equals(JarFile.MANIFEST_NAME);
    }

    /**
     * Parses Manifest from a byte array.
     * @param content the bytes of the manifest
     * @return the Manifest
     * @throws IOException if the manifest cannot be parsed
     */
    public static Manifest readManifest(byte[] content) throws IOException {
        return new Manifest(new ByteArrayInputStream(content));
    }

    /**
     * Checks if the manifest declares JAR to be multi-release.
     *
     * @param manifest the manifest
     * @return {@code true} if the manifest declares the multi-release JAR
     */
    public static boolean isMultiReleaseJarManifest(Manifest manifest) {
        return Boolean.parseBoolean(getManifestMainAttribute(manifest, MULTI_RELEASE_ATTRIBUTE));
    }

    @Nullable
    private static String getManifestMainAttribute(Manifest manifest, String name) {
        return manifest.getMainAttributes().getValue(name);
    }

    /**
     * Checks if the entry path is inside a versioned directory and returns the corresponding major version of Java platform.
     * Returns empty Optional if this entry isn't inside a versioned directory.
     *
     * @param entryPath the full path to the entry inside the JAR
     * @return major version of Java platform for which this entry is intended if it is in the versioned directory or empty Optional
     *
     * @see <a href="https://docs.oracle.com/en/java/javase/20/docs/specs/jar/jar.html#multi-release-jar-files">MR JAR specification</a>
     */
    public static OptionalInt getVersionedDirectoryMajorVersion(String entryPath) {
        Matcher match = VERSIONED_JAR_ENTRY_PATH.matcher(entryPath);
        if (match.matches()) {
            try {
                int version = Integer.parseInt(match.group(1));
                return OptionalInt.of(version);
            } catch (NumberFormatException ignored) {
                // Even though the pattern ensures that the version name is all digits, it fails to parse, probably because it is too big.
                // Technically it may be a valid MR JAR for Java >Integer.MAX_VALUE, but we are too far away from this.
                // We assume that JAR author didn't intend it to be a versioned directory.
            }
        }
        // The entry is not in the versioned directory.
        return OptionalInt.empty();
    }
}
