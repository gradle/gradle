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

package org.gradle.internal.fingerprint;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.stream.Stream;

public interface FileCollectionFingerprinterRegistrar {
    Collection<FileCollectionFingerprinter> getRegistrants();

    static FileCollectionFingerprinterRegistrar of(FileCollectionFingerprinter... fingerprinters) {
        return () -> ImmutableList.copyOf(fingerprinters);
    }

    static FileCollectionFingerprinterRegistrar of(Stream<? extends FileCollectionFingerprinter> stream) {
        return of(stream.toArray(FileCollectionFingerprinter[]::new));
    }
}
