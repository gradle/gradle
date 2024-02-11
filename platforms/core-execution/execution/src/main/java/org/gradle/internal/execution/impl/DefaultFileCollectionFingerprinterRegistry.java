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

package org.gradle.internal.execution.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.internal.execution.FileCollectionFingerprinter;
import org.gradle.internal.execution.FileCollectionFingerprinterRegistry;
import org.gradle.internal.execution.FileNormalizationSpec;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DefaultFileCollectionFingerprinterRegistry implements FileCollectionFingerprinterRegistry {
    private final Map<FileNormalizationSpec, FileCollectionFingerprinter> fingerprinters;

    public DefaultFileCollectionFingerprinterRegistry(Collection<FingerprinterRegistration> registrations) {
        this.fingerprinters = ImmutableMap.copyOf(entriesFrom(registrations));
    }

    private List<Map.Entry<FileNormalizationSpec, FileCollectionFingerprinter>> entriesFrom(Collection<FingerprinterRegistration> registrations) {
        return registrations.stream().map(registration -> Maps.immutableEntry(registration.getSpec(), registration.getFingerprinter())).collect(ImmutableList.toImmutableList());
    }

    @Override
    public FileCollectionFingerprinter getFingerprinter(FileNormalizationSpec spec) {
        FileCollectionFingerprinter fingerprinter = fingerprinters.get(spec);
        if (fingerprinter == null) {
            throw new IllegalStateException(String.format("No fingerprinter registered with '%s' normalization, directory sensitivity '%s' and line ending normalization '%s'", spec.getNormalizer(), spec.getDirectorySensitivity().name(), spec.getLineEndingNormalization()));
        }
        return fingerprinter;
    }
}
