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

import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.hashing.ConfigurableNormalizer;

public abstract class AbstractDirectorySensitiveFingerprintingStrategy extends AbstractFingerprintingStrategy {
    private final DirectorySensitivity directorySensitivity;

    public AbstractDirectorySensitiveFingerprintingStrategy(String identifier, DirectorySensitivity directorySensitivity, ConfigurableNormalizer contentNormalizer) {
        super(identifier, hasher -> {
            contentNormalizer.appendConfigurationToHasher(hasher);
            hasher.putInt(directorySensitivity.ordinal());
        });
        this.directorySensitivity = directorySensitivity;
    }

    protected DirectorySensitivity getDirectorySensitivity() {
        return directorySensitivity;
    }
}
