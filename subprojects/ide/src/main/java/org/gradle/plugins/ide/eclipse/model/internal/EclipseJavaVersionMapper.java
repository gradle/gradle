/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.JavaVersion;

/**
 * Utility class for saving Java versions in Eclipse descriptors.
 */
public class EclipseJavaVersionMapper {

    private EclipseJavaVersionMapper() {
    }

    /**
     * Converts the target Java version to to its Eclipse-specific representation.
     *
     * @param version the target Java version
     * @return the Eclipse-specific representation of the version
     */
    public static String toEclipseJavaVersion(JavaVersion version) {
        switch (version.getMajorVersionNumber()) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                return version.toString();
            case 9:
            case 10:
            default:
                return version.getMajorVersion();
        }
    }
}
