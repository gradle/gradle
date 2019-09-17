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


import org.gradle.util.VersionNumber

class GroovyCoverage {
    private static final String[] PREVIOUS = ['1.5.8', '1.6.9', '1.7.11', '1.8.8', '2.0.5', '2.1.9', '2.2.2', '2.3.10', '2.4.15', '2.5.8']
    static final String[] ALL

    private static final MINIMUM_WITH_GROOVYDOC_SUPPORT = VersionNumber.parse("1.6.9")
    static final String[] SUPPORTS_GROOVYDOC

    private static final MINIMUM_WITH_TIMESTAMP_SUPPORT = VersionNumber.parse("2.4.6")
    static final String[] SUPPORTS_TIMESTAMP

    private static final MINIMUM_WITH_PARAMETERS_METADATA_SUPPORT = VersionNumber.parse("2.5.0")
    static final String[] SUPPORTS_PARAMETERS

    static {
        def allVersions = [*PREVIOUS]

        // Only test current Groovy version if it isn't a SNAPSHOT
        if (!GroovySystem.version.endsWith("-SNAPSHOT")) {
            allVersions += GroovySystem.version
        }

        ALL = allVersions
        SUPPORTS_GROOVYDOC = allVersions.findAll {
            VersionNumber.parse(it) >= MINIMUM_WITH_GROOVYDOC_SUPPORT
        }
        SUPPORTS_TIMESTAMP = allVersions.findAll {
            VersionNumber.parse(it) >= MINIMUM_WITH_TIMESTAMP_SUPPORT
        }
        SUPPORTS_PARAMETERS = allVersions.findAll {
            VersionNumber.parse(it) >= MINIMUM_WITH_PARAMETERS_METADATA_SUPPORT
        }
    }
}
