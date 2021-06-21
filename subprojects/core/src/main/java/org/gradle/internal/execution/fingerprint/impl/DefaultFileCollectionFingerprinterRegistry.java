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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.execution.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.execution.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.execution.fingerprint.FileNormalizationSpec;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.IgnoredPathInputNormalizer;
import org.gradle.internal.fingerprint.LineEndingNormalization;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.execution.fingerprint.impl.DefaultFileNormalizationSpec.from;

public class DefaultFileCollectionFingerprinterRegistry implements FileCollectionFingerprinterRegistry {
    private final Map<FileNormalizationSpec, FileCollectionFingerprinter> fingerprinters;

    /**
     * These are the normalizers where it does not make sense to ignore empty directories.  If the user specifies non-sensical empty directory
     * normalization (e.g. via the runtime api) we will ignore that criteria when selecting a fingerprinter.
     */
    private final static Set<Class<? extends FileNormalizer>> DIRECTORY_INSENSITIVE_NORMALIZERS = ImmutableSet.of(
        ClasspathNormalizer.class,
        CompileClasspathNormalizer.class,
        IgnoredPathInputNormalizer.class
    );

    /**
     * These are the normalizers where it does not make sense to ignore line endings.  If the user specifies non-sensical line ending
     * normalization (e.g. via the runtime api) we will ignore that criteria when selecting a fingerprinter.
     */
    private final static Set<Class<? extends FileNormalizer>> LINE_ENDING_INSENSITIVE_NORMALIZERS = ImmutableSet.of(
        CompileClasspathNormalizer.class
    );

    public DefaultFileCollectionFingerprinterRegistry(Collection<FileCollectionFingerprinter> fingerprinters) {
        this.fingerprinters = ImmutableMap.copyOf(Maps.uniqueIndex(fingerprinters, input -> from(input.getRegisteredType(), input.getDirectorySensitivity(), input.getLineEndingNormalization())));
    }

    @Override
    public FileCollectionFingerprinter getFingerprinter(FileNormalizationSpec spec) {
        FileCollectionFingerprinter fingerprinter = fingerprinters.get(normalizeCriteria(spec));
        if (fingerprinter == null) {
            throw new IllegalStateException(String.format("No fingerprinter registered with type '%s', directory sensitivity '%s' and line ending normalization '%s'", spec.getNormalizer().getName(), spec.getDirectorySensitivity().name(), spec.getLineEndingNormalization()));
        }
        return fingerprinter;
    }

    private static FileNormalizationSpec normalizeCriteria(FileNormalizationSpec spec) {
        DirectorySensitivity effectiveDirectorySensitivity = DIRECTORY_INSENSITIVE_NORMALIZERS.contains(spec.getNormalizer()) ? DirectorySensitivity.DEFAULT : spec.getDirectorySensitivity();
        LineEndingNormalization effectiveLineEndingNormalization = LINE_ENDING_INSENSITIVE_NORMALIZERS.contains(spec.getNormalizer()) ?  LineEndingNormalization.DEFAULT : spec.getLineEndingNormalization();
        return from(spec.getNormalizer(), effectiveDirectorySensitivity, effectiveLineEndingNormalization);
    }
}
