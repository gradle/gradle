/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.tooling.internal.consumer.daemon;

import org.gradle.util.GradleVersion;

/**
 * Picks the right {@link DaemonContextReader} for a given Gradle version.
 *
 * <p>Boundaries:
 * <ul>
 *   <li>V1: 5.0 – 8.2</li>
 *   <li>V2: 8.3 – 8.7</li>
 *   <li>V3: 8.8 – 8.9</li>
 *   <li>V4: 8.10+</li>
 * </ul>
 *
 * <p>Newer releases that change {@code DefaultDaemonContext} serialization must add a
 * new {@code DaemonContextReaderVN} and a new branch here. The {@code
 * CurrentFormatDriftTest} guard fails CI before the new format ships unguarded.
 */
final class DaemonContextReaders {
    static final GradleVersion V_8_3 = GradleVersion.version("8.3");
    static final GradleVersion V_8_8 = GradleVersion.version("8.8");
    static final GradleVersion V_8_10 = GradleVersion.version("8.10");

    private DaemonContextReaders() {
    }

    static DaemonContextReader forVersion(String gradleVersion) {
        GradleVersion v = GradleVersion.version(gradleVersion).getBaseVersion();
        if (v.compareTo(V_8_10) >= 0) {
            return new DaemonContextReaderV4();
        }
        if (v.compareTo(V_8_8) >= 0) {
            return new DaemonContextReaderV3();
        }
        if (v.compareTo(V_8_3) >= 0) {
            return new DaemonContextReaderV2();
        }
        return new DaemonContextReaderV1();
    }
}
