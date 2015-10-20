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

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

public class FileTreeElementHasher {
    private static final byte HASH_PATH_SEPARATOR = (byte) '/';
    private static final byte HASH_RECORD_SEPARATOR = (byte) '\n';

    public static final int calculateHashForFilePaths(Collection<FileTreeElement> allFileTreeElements) {
        SortedSet<FileTreeElement> sortedFileTreeElement = asSortedSet(allFileTreeElements);

        Hasher hasher = Hashing.adler32().newHasher();
        for (FileTreeElement fileTreeElement : sortedFileTreeElement) {
            for (String pathPart : fileTreeElement.getRelativePath().getSegments()) {
                hasher.putUnencodedChars(pathPart);
                hasher.putByte(HASH_PATH_SEPARATOR);
            }
            hasher.putByte(HASH_RECORD_SEPARATOR);
        }
        return hasher.hash().asInt();
    }

    private static SortedSet<FileTreeElement> asSortedSet(Collection<FileTreeElement> allFileTreeElements) {
        if (allFileTreeElements instanceof SortedSet) {
            return (SortedSet<FileTreeElement>) allFileTreeElements;
        }
        SortedSet<FileTreeElement> sortedFileTreeElement = new TreeSet<FileTreeElement>(FileTreeElementComparator.INSTANCE);
        sortedFileTreeElement.addAll(allFileTreeElements);
        return sortedFileTreeElement;
    }
}
