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

package org.gradle.testing.fixture

import org.gradle.api.JavaVersion
import org.gradle.util.VersionNumber

class GroovyCoverage {
    private static final String[] PREVIOUS = ['1.5.8', '1.6.9', '1.7.11', '1.8.8', '2.0.5', '2.1.9', '2.2.2', '2.3.10', '2.4.15', '2.5.8', '2.5.10-SNAPSHOT']
    private static final String[] ALL

    static final List<String> SUPPORTS_GROOVYDOC
    static final List<String> SUPPORTS_TIMESTAMP
    static final List<String> SUPPORTS_PARAMETERS

    static {
        def allVersions = [*PREVIOUS]

        // Only test current Groovy version if it isn't a SNAPSHOT
        if (!GroovySystem.version.endsWith("-SNAPSHOT")) {
            allVersions += GroovySystem.version
        }

        ALL = allVersions
        SUPPORTS_GROOVYDOC = versionsAbove(VersionNumber.parse("1.6.9"))
        SUPPORTS_TIMESTAMP = versionsAbove(VersionNumber.parse("2.4.6"))
        SUPPORTS_PARAMETERS = versionsAbove(VersionNumber.parse("2.5.0"))
    }

    static List<String> getSupportedVersionsByJdk() {
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_14)) {
            return ALL.findAll {
                def version = VersionNumber.parse(it)
                version <= VersionNumber.parse('2.2.2') || version >= VersionNumber.parse('2.5.10')
            }.asImmutable()
        }
        return ALL
    }

    private static List<String> versionsAbove(VersionNumber threshold) {
        getSupportedVersionsByJdk().findAll { VersionNumber.parse(it) >= threshold }.asImmutable()
    }
}
