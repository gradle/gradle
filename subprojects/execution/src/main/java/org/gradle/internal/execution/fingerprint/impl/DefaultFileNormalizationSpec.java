/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.execution.fingerprint.FileNormalizationSpec;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.LineEndingSensitivity;

import java.util.Objects;

public class DefaultFileNormalizationSpec implements FileNormalizationSpec {
    private final Class<? extends FileNormalizer> normalizer;
    private final DirectorySensitivity directorySensitivity;
    private final LineEndingSensitivity lineEndingSensitivity;

    private DefaultFileNormalizationSpec(Class<? extends FileNormalizer> normalizer, DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingSensitivity) {
        this.normalizer = normalizer;
        this.directorySensitivity = directorySensitivity;
        this.lineEndingSensitivity = lineEndingSensitivity;
    }

    @Override
    public Class<? extends FileNormalizer> getNormalizer() {
        return normalizer;
    }

    @Override
    public DirectorySensitivity getDirectorySensitivity() {
        return directorySensitivity;
    }

    @Override
    public LineEndingSensitivity getLineEndingNormalization() {
        return lineEndingSensitivity;
    }

    public static FileNormalizationSpec from(Class<? extends FileNormalizer> normalizer, DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingSensitivity) {
        return new DefaultFileNormalizationSpec(normalizer, directorySensitivity, lineEndingSensitivity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultFileNormalizationSpec that = (DefaultFileNormalizationSpec) o;
        return normalizer.equals(that.normalizer) &&
            directorySensitivity == that.directorySensitivity &&
            lineEndingSensitivity == that.lineEndingSensitivity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizer, directorySensitivity, lineEndingSensitivity);
    }
}
