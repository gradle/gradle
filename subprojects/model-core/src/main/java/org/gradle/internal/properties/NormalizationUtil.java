/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.properties;

import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.fingerprint.InputNormalizer;
import org.gradle.internal.fingerprint.Normalizer;

public class NormalizationUtil {
    public static Normalizer determineNormalizerForPathSensitivity(PathSensitivity pathSensitivity) {
        switch (pathSensitivity) {
            case NONE:
                return InputNormalizer.IGNORE_PATH;
            case NAME_ONLY:
                return InputNormalizer.NAME_ONLY;
            case RELATIVE:
                return InputNormalizer.RELATIVE_PATH;
            case ABSOLUTE:
                return InputNormalizer.ABSOLUTE_PATH;
            default:
                throw new IllegalArgumentException("Unknown path sensitivity: " + pathSensitivity);
        }
    }

}
