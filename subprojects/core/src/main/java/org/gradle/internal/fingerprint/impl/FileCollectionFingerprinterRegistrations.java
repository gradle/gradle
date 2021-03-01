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

package org.gradle.internal.fingerprint.impl;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.execution.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.execution.fingerprint.FileCollectionSnapshotter;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.hashing.FileContentHasher;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileCollectionFingerprinterRegistrations {
    private final List<FileCollectionFingerprinter> registrants;

    public FileCollectionFingerprinterRegistrations(StringInterner stringInterner, FileCollectionSnapshotter fileCollectionSnapshotter, FileContentHasher fileContentHasher) {
        this.registrants = Arrays.stream(DirectorySensitivity.values())
            .flatMap(directorySensitivity ->
                Stream.of(
                    new AbsolutePathFileCollectionFingerprinter(directorySensitivity, fileCollectionSnapshotter, fileContentHasher),
                    new RelativePathFileCollectionFingerprinter(stringInterner, directorySensitivity, fileCollectionSnapshotter, fileContentHasher),
                    new NameOnlyFileCollectionFingerprinter(directorySensitivity, fileCollectionSnapshotter, fileContentHasher)
                )
            ).collect(Collectors.toList());
        this.registrants.add(new IgnoredPathFileCollectionFingerprinter(fileCollectionSnapshotter, fileContentHasher));
    }

    public FileCollectionFingerprinterRegistrations(StringInterner stringInterner, FileCollectionSnapshotter fileCollectionSnapshotter) {
        this(stringInterner, fileCollectionSnapshotter, FileContentHasher.NONE);
    }

    public List<? extends FileCollectionFingerprinter> getRegistrants() {
        return registrants;
    }
}
