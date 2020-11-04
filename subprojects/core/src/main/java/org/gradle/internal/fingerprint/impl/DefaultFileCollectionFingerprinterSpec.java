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

package org.gradle.internal.fingerprint.impl;

import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.fingerprint.DirectorySensitiveNormalizer;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterSpec;

import java.util.Objects;

public class DefaultFileCollectionFingerprinterSpec implements FileCollectionFingerprinterSpec {
    private final Class<? extends FileNormalizer> normalizer;
    private final DirectorySensitivity directorySensitivity;

    public DefaultFileCollectionFingerprinterSpec(Class<? extends FileNormalizer> normalizer, DirectorySensitivity directorySensitivity) {
        this.normalizer = normalizer;
        this.directorySensitivity = directorySensitivity;
    }

    @Override
    public Class<? extends FileNormalizer> getNormalizer() {
        return normalizer;
    }

    @Override
    public DirectorySensitivity getEmptyDirectorySensitivity() {
        return directorySensitivity;
    }

    public static FileCollectionFingerprinterSpec from(Class<? extends FileNormalizer> normalizer, DirectorySensitivity directorySensitivity) {
        if (DirectorySensitiveNormalizer.class.isAssignableFrom(normalizer)) {
            return new DefaultFileCollectionFingerprinterSpec(normalizer, directorySensitivity);
        } else {
            return new DefaultFileCollectionFingerprinterSpec(normalizer, DirectorySensitivity.NONE);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultFileCollectionFingerprinterSpec that = (DefaultFileCollectionFingerprinterSpec) o;
        return normalizer.equals(that.normalizer) &&
            directorySensitivity == that.directorySensitivity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizer, directorySensitivity);
    }
}
