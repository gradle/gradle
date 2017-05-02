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

import com.google.common.hash.HashCode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.util.DeprecationLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Hashes the contents of a jar file with the given {@link ContentHasher}.
 */
public class JarContentHasher implements ContentHasher {
    private final ContentHasher contentHasher;
    private final StringInterner stringInterner;

    public JarContentHasher(ContentHasher contentHasher, StringInterner stringInterner) {
        this.contentHasher = contentHasher;
        this.stringInterner = stringInterner;
    }

    public HashCode hash(RegularFileSnapshot jarFile) {
        String jarFilePath = jarFile.getPath();
        InputStream fileInputStream = null;
        try {
            ClasspathEntrySnapshotBuilder entryResourceCollectionBuilder = new ClasspathEntrySnapshotBuilder(stringInterner);
            fileInputStream = Files.newInputStream(Paths.get(jarFilePath));
            ZipInputStream zipInput = new ZipInputStream(fileInputStream);
            ZipEntry zipEntry;

            while ((zipEntry = zipInput.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                HashCode hash = contentHasher.hash(zipEntry, new CloseShieldInputStream(zipInput));
                entryResourceCollectionBuilder.visitZipFileEntry(zipEntry, hash);
            }
            return entryResourceCollectionBuilder.getHash();
        } catch (ZipException e) {
            // ZipExceptions point to a problem with the Zip, we try to be lenient for now.
            return hashMalformedZip(jarFile);
        } catch (IOException e) {
            // IOExceptions other than ZipException are failures.
            throw new UncheckedIOException("Error snapshotting jar [" + jarFile.getName() + "]", e);
        } catch (Exception e) {
            // Other Exceptions can be thrown by invalid zips, too. See https://github.com/gradle/gradle/issues/1581.
            return hashMalformedZip(jarFile);
        } finally {
            IOUtils.closeQuietly(fileInputStream);
        }
    }

    private HashCode hashMalformedZip(FileSnapshot fileSnapshot) {
        DeprecationLogger.nagUserWith("Malformed jar [" + fileSnapshot.getName() + "] found on classpath. Gradle 5.0 will no longer allow malformed jars on a classpath.");
        return fileSnapshot.getContent().getContentMd5();
    }

    @Override
    public HashCode hash(ZipEntry zipEntry, InputStream zipInput) throws IOException {
        throw new UnsupportedOperationException("Hashing zips in zips is not yet supported");
    }
}
