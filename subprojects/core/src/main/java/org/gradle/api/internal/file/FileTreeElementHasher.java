/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.api.file.FileTreeElement;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

public class FileTreeElementHasher {
    private static final byte HASH_PATH_SEPARATOR = (byte) '/';
    private static final byte HASH_FIELD_SEPARATOR = (byte) '\t';
    private static final byte HASH_RECORD_SEPARATOR = (byte) '\n';

    public static final int calculateHashForFileMetadata(Collection<? extends FileTreeElement> allFileTreeElements) {
        FileTreeElement[] sortedFileTreeElement = sortForHashing(allFileTreeElements);

        BufferedStreamingHasher hasher = new BufferedStreamingHasher();
        Encoder encoder = hasher.getEncoder();
        try {
            for (FileTreeElement fileTreeElement : sortedFileTreeElement) {
                for (String pathPart : fileTreeElement.getRelativePath().getSegments()) {
                    encoder.writeString(pathPart);
                    encoder.writeByte(HASH_PATH_SEPARATOR);
                }
                if (!fileTreeElement.isDirectory()) {
                    encoder.writeByte(HASH_FIELD_SEPARATOR);
                    encoder.writeLong(fileTreeElement.getSize());
                    encoder.writeByte(HASH_FIELD_SEPARATOR);
                    encoder.writeLong(fileTreeElement.getLastModified());
                }
                encoder.writeByte(HASH_RECORD_SEPARATOR);
            }
            return hasher.checksum();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static Hasher createHasher() {
        return Hashing.crc32().newHasher();
    }

    public static final int calculateHashForFilePaths(Collection<? extends FileTreeElement> allFileTreeElements) {
        FileTreeElement[] sortedFileTreeElement = sortForHashing(allFileTreeElements);

        BufferedStreamingHasher hasher = new BufferedStreamingHasher();
        Encoder encoder = hasher.getEncoder();
        try {
            for (FileTreeElement fileTreeElement : sortedFileTreeElement) {
                for (String pathPart : fileTreeElement.getRelativePath().getSegments()) {
                    encoder.writeString(pathPart);
                    encoder.writeByte(HASH_PATH_SEPARATOR);
                }
                encoder.writeByte(HASH_RECORD_SEPARATOR);
            }
            return hasher.checksum();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

    }

    private static FileTreeElement[] sortForHashing(Collection<? extends FileTreeElement> allFileTreeElements) {
        FileTreeElement[] sortedFileTreeElement = allFileTreeElements.toArray(new FileTreeElement[0]);
        Arrays.sort(sortedFileTreeElement, FileTreeElementComparator.INSTANCE);
        return sortedFileTreeElement;
    }
}
