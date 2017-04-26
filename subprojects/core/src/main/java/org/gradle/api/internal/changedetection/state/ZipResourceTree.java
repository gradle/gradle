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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

public class ZipResourceTree {
    private final FileSnapshot zipFile;
    private final ClasspathContentHasher classpathContentHasher;
    private final StringInterner stringInterner;

    public ZipResourceTree(FileSnapshot zipFile, ClasspathContentHasher classpathContentHasher, StringInterner stringInterner) {
        this.zipFile = zipFile;
        this.classpathContentHasher = classpathContentHasher;
        this.stringInterner = stringInterner;
    }

    public HashCode getHash() {
        File jarFilePath = new File(zipFile.getPath());
        ZipInputStream zipInput = null;
        try {
            ClasspathEntryResourceCollectionBuilder entryResourceCollectionBuilder = new ClasspathEntryResourceCollectionBuilder(stringInterner);
            zipInput = new ZipInputStream(new FileInputStream(jarFilePath));
            ZipEntry zipEntry;

            while ((zipEntry = zipInput.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                HashCode hash = classpathContentHasher.getHash(zipEntry, new CloseShieldInputStream(zipInput));
                entryResourceCollectionBuilder.visitZipFileEntry(zipEntry, hash);
            }
            return entryResourceCollectionBuilder.getHash();
        } catch (ZipException e) {
            // ZipExceptions point to a problem with the Zip, we try to be lenient for now.
            return hashMalformedZip(zipFile);
        } catch (IOException e) {
            // IOExceptions other than ZipException are failures.
            throw new UncheckedIOException("Error snapshotting jar [" + zipFile.getName() + "]", e);
        } catch (Exception e) {
            // Other Exceptions can be thrown by invalid zips, too. See https://github.com/gradle/gradle/issues/1581.
            return hashMalformedZip(zipFile);
        } finally {
            IOUtils.closeQuietly(zipInput);
        }
    }

    private HashCode hashMalformedZip(FileSnapshot fileSnapshot) {
        DeprecationLogger.nagUserWith("Malformed jar [" + fileSnapshot.getName() + "] found on classpath. Gradle 5.0 will no longer allow malformed jars on a classpath.");
        return fileSnapshot.getContent().getContentMd5();
    }
}
