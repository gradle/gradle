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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.tasks.PathSensitivity;

public enum PathNormalizationStrategy {

    /**
     * Use the absolute path of the files.
     */
    ABSOLUTE,

    /**
     * Like absolute, but ignoring missing files.
     */
    OUTPUT,

    /**
     * Use the location of the file related to a hierarchy.
     */
    RELATIVE,

    /**
     * Use the file name only.
     */
    NAME_ONLY,

    /**
     * Ignore the file path completely.
     */
    NONE;

    public static PathNormalizationStrategy from(PathSensitivity pathSensitivity) {
        switch (pathSensitivity) {
            case ABSOLUTE:
                return ABSOLUTE;
            case RELATIVE:
                return RELATIVE;
            case NAME_ONLY:
                return NAME_ONLY;
            case NONE:
                return NONE;
            default:
                throw new IllegalArgumentException("Unknown path usage: " + pathSensitivity);
        }
    }
}
