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

package org.gradle.internal.execution.impl;

import org.gradle.internal.execution.FileCollectionFingerprinter;
import org.gradle.internal.execution.FileNormalizationSpec;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.LineEndingSensitivity;

import java.util.Objects;

public class FingerprinterRegistration {
    private final FileNormalizationSpec spec;
    private final FileCollectionFingerprinter fingerprinter;

    private FingerprinterRegistration(FileNormalizationSpec spec, FileCollectionFingerprinter fingerprinter) {
        this.spec = spec;
        this.fingerprinter = fingerprinter;
    }

    public FileNormalizationSpec getSpec() {
        return spec;
    }

    public FileCollectionFingerprinter getFingerprinter() {
        return fingerprinter;
    }

    public static FingerprinterRegistration registration(DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingSensitivity, FileCollectionFingerprinter fingerprinter) {
        return new FingerprinterRegistration(
            DefaultFileNormalizationSpec.from(fingerprinter.getNormalizer(), directorySensitivity, lineEndingSensitivity),
            fingerprinter
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FingerprinterRegistration that = (FingerprinterRegistration) o;
        return spec.equals(that.spec) && fingerprinter.equals(that.fingerprinter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spec);
    }
}
