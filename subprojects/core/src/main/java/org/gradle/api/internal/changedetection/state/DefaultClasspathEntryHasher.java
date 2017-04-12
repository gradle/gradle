/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.base.Charsets;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

public class DefaultClasspathEntryHasher implements ClasspathEntryHasher {
    private static final byte[] SIGNATURE = Hashing.md5().hashString(DefaultClasspathEntryHasher.class.getName(), Charsets.UTF_8).asBytes();
    private final ClasspathContentHasher classpathContentHasher;

    public DefaultClasspathEntryHasher(ClasspathContentHasher classpathContentHasher) {
        this.classpathContentHasher = classpathContentHasher;
    }

    @Override
    public HashCode hash(SnapshottableResource fileSnapshot) {
        if (fileSnapshot.getType() == FileType.Directory || fileSnapshot.getType() == FileType.Missing) {
            return null;
        }

        String name = fileSnapshot.getName();
        final Hasher hasher = createHasher();
        if (FileUtils.isJar(name)) {
            return hashJar(fileSnapshot, hasher, classpathContentHasher);
        } else {
            return hashFile(fileSnapshot, hasher, classpathContentHasher);
        }
    }

    private HashCode hashJar(SnapshottableResource fileSnapshot, Hasher hasher, ClasspathContentHasher classpathContentHasher) {
        File jarFilePath = new File(fileSnapshot.getPath());
        ZipInputStream zipInput = null;
        try {
            zipInput = new ZipInputStream(new FileInputStream(jarFilePath));

            ZipEntry zipEntry;
            Multimap<String, HashCode> entriesByName = MultimapBuilder.hashKeys().arrayListValues().build();
            while ((zipEntry = zipInput.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                HashCode hash = hashZipEntry(zipInput, zipEntry, classpathContentHasher);
                if (hash != null) {
                    entriesByName.put(zipEntry.getName(), hash);
                }
            }
            Map<String, Collection<HashCode>> map = entriesByName.asMap();
            // Ensure we hash the zip entries in a deterministic order
            String[] keys = map.keySet().toArray(new String[0]);
            Arrays.sort(keys);
            for (String key : keys) {
                for (HashCode hash : map.get(key)) {
                    hasher.putBytes(hash.asBytes());
                }
            }
            return hasher.hash();
        } catch (ZipException e) {
            // ZipExceptions point to a problem with the Zip, we try to be lenient for now.
            return hashMalformedZip(fileSnapshot, hasher, classpathContentHasher);
        } catch (IOException e) {
            // IOExceptions other than ZipException are failures.
            throw new UncheckedIOException("Error snapshotting jar [" + fileSnapshot.getName() + "]", e);
        } catch (Exception e) {
            // Other Exceptions can be thrown by invalid zips, too. See https://github.com/gradle/gradle/issues/1581.
            return hashMalformedZip(fileSnapshot, hasher, classpathContentHasher);
        } finally {
            IOUtils.closeQuietly(zipInput);
        }
    }

    private HashCode hashMalformedZip(SnapshottableResource fileSnapshot, Hasher hasher, ClasspathContentHasher classpathContentHasher) {
        DeprecationLogger.nagUserWith("Malformed jar [" + fileSnapshot.getName() + "] found on classpath. Gradle 5.0 will no longer allow malformed jars on a classpath.");
        return hashFile(fileSnapshot, hasher, classpathContentHasher);
    }

    private HashCode hashZipEntry(InputStream inputStream, ZipEntry zipEntry, ClasspathContentHasher classpathContentHasher) throws IOException {
        Hasher hasher = new TrackingHasher(Hashing.md5().newHasher());
        classpathContentHasher.appendContent(zipEntry.getName(), inputStream, hasher);
        return hasher.hash();
    }

    private HashCode hashFile(SnapshottableResource fileSnapshot, Hasher hasher, ClasspathContentHasher classpathContentHasher) {
        InputStream inputStream = null;
        try {
            inputStream = fileSnapshot.read();
            classpathContentHasher.appendContent(fileSnapshot.getName(), inputStream, hasher);
            return hasher.hash();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    private Hasher createHasher() {
        return new TrackingHasher(Hashing.md5().newHasher().putBytes(SIGNATURE));
    }
}
