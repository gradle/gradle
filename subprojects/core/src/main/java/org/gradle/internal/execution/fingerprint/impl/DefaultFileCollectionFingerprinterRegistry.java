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

package org.gradle.internal.execution.fingerprint.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.internal.execution.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.execution.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.execution.fingerprint.FileNormalizationSpec;

import java.util.Collection;
import java.util.Map;

import static org.gradle.internal.execution.fingerprint.impl.DefaultFileNormalizationSpec.from;

public class DefaultFileCollectionFingerprinterRegistry implements FileCollectionFingerprinterRegistry {
    private final Map<FileNormalizationSpec, FileCollectionFingerprinter> fingerprinters;

    public DefaultFileCollectionFingerprinterRegistry(Collection<FileCollectionFingerprinter> fingerprinters) {
        this.fingerprinters = ImmutableMap.copyOf(Maps.uniqueIndex(fingerprinters, input -> from(input.getRegisteredType(), input.getDirectorySensitivity())));
    }

    @Override
    public FileCollectionFingerprinter getFingerprinter(FileNormalizationSpec spec) {
        FileCollectionFingerprinter fingerprinter = fingerprinters.get(spec);
        if (fingerprinter == null) {
            throw new IllegalStateException(String.format("No fingerprinter registered with type '%s' and directory sensitivity '%s'", spec.getNormalizer().getName(), spec.getDirectorySensitivity().name()));
        }
        return fingerprinter;
    }
}
