/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.fingerprint.impl;

import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

public class DefaultFileSystemLocationFingerprint implements FileSystemLocationFingerprint {
    private final HashCode normalizedContentHash;
    private final String normalizedPath;

    public DefaultFileSystemLocationFingerprint(String normalizedPath, FileType type, HashCode contentHash) {
        this.normalizedContentHash = hashForType(type, contentHash);
        this.normalizedPath = normalizedPath;
    }

    public DefaultFileSystemLocationFingerprint(String normalizedPath, FileSystemLocationSnapshot snapshot) {
        this(normalizedPath, snapshot.getType(), snapshot.getHash());
    }

    private static HashCode hashForType(FileType fileType, HashCode hash) {
        switch (fileType) {
            case Directory:
                return DIR_SIGNATURE;
            case Missing:
                return MISSING_FILE_SIGNATURE;
            case RegularFile:
                return hash;
            default:
                throw new IllegalStateException("Unknown file type: " + fileType);
        }
    }

    @Override
    public final void appendToHasher(Hasher hasher) {
        hasher.putString(getNormalizedPath());
        hasher.putHash(getNormalizedContentHash());
    }

    @Override
    public FileType getType() {
        if (normalizedContentHash == DIR_SIGNATURE) {
            return FileType.Directory;
        } else if (normalizedContentHash == MISSING_FILE_SIGNATURE) {
            return FileType.Missing;
        } else {
            return FileType.RegularFile;
        }
    }

    @Override
    public String getNormalizedPath() {
        return normalizedPath;
    }

    @Override
    public HashCode getNormalizedContentHash() {
        return normalizedContentHash;
    }

    @Override
    public final int compareTo(FileSystemLocationFingerprint o) {
        int result = getNormalizedPath().compareTo(o.getNormalizedPath());
        if (result == 0) {
            result = getNormalizedContentHash().compareTo(o.getNormalizedContentHash());
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultFileSystemLocationFingerprint that = (DefaultFileSystemLocationFingerprint) o;

        if (!normalizedContentHash.equals(that.normalizedContentHash)) {
            return false;
        }
        return normalizedPath.equals(that.normalizedPath);
    }

    @Override
    public int hashCode() {
        int result = normalizedContentHash.hashCode();
        result = 31 * result + normalizedPath.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("'%s' / %s",
            getNormalizedPath(),
            getHashOrTypeToDisplay()
        );
    }

    private Object getHashOrTypeToDisplay() {
        switch (getType()) {
            case Directory:
                return "DIR";
            case Missing:
                return "MISSING";
            default:
                return normalizedContentHash;
        }
    }
}
