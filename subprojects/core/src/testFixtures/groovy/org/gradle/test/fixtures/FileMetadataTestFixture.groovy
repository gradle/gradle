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

package org.gradle.test.fixtures

import org.gradle.api.JavaVersion
import org.gradle.internal.os.OperatingSystem

class FileMetadataTestFixture {
    static maybeRoundLastModified(long lastModified) {
        // Some Java 8 versions on Unix only capture the seconds in lastModified, so we cut off the milliseconds returned from the filesystem as well.
        // For example, Oracle JDK 1.8.0_181-b13 does not capture milliseconds, while OpenJDK 1.8.0_242-b08 does.
        return (JavaVersion.current().java9Compatible || OperatingSystem.current().windows)
            ? lastModified
            : lastModified.intdiv(1000) * 1000
    }
}
