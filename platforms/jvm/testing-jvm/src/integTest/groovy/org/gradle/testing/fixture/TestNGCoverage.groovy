/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.util.internal.VersionNumber

class TestNGCoverage {
    final static String NEWEST = '7.5'

    private static final String FIXED_ILLEGAL_ACCESS = '5.14.6' // Oldest version to support JDK 16+ without explicit --add-opens

    private static final String BROKEN_ICLASS_LISTENER = '6.9.10' // Introduces initial, buggy IClassListener
    private static final String FIXED_ICLASS_LISTENER = '6.9.13.3' // Introduces fixed IClassListener

    private static final String FIRST_PRESERVE_ORDER_SUPPORT = '5.14.5' // First version to support preserve-order
    private static final String BEFORE_BROKEN_PRESERVE_ORDER = '6.1.1' // Latest version before introduction of cbeust/testng#639 bug
    private static final String FIXED_BROKEN_PRESERVE_ORDER = '6.9.4'  // Fixes cbeust/testng#639 for preserve-order

    private static final Set<String> VERSIONS = [
        '5.12.1', // Newest version without TestNG#setConfigFailurePolicy method (Added in 5.13)
        FIXED_ILLEGAL_ACCESS,
        BEFORE_BROKEN_PRESERVE_ORDER,
        FIXED_BROKEN_PRESERVE_ORDER,
        BROKEN_ICLASS_LISTENER,
        FIXED_ICLASS_LISTENER,
        NEWEST
    ]

    static final Set<String> SUPPORTED_BY_JDK = testNgVersionsSupportedByJdk(VERSIONS, JavaVersion.current())
    static final Set<String> SUPPORTS_PRESERVE_ORDER = SUPPORTED_BY_JDK.findAll {
        VersionNumber version = VersionNumber.parse(it)
        version >= VersionNumber.parse(FIRST_PRESERVE_ORDER_SUPPORT)
            && !(version > VersionNumber.parse(BEFORE_BROKEN_PRESERVE_ORDER) && version < VersionNumber.parse(FIXED_BROKEN_PRESERVE_ORDER))
    }
    static final Set<String> SUPPORTS_GROUP_BY_INSTANCES = SUPPORTED_BY_JDK.findAll { VersionNumber.parse(it) >= VersionNumber.parse('6.1') }
    static final Set<String> SUPPORTS_ICLASS_LISTENER = SUPPORTED_BY_JDK.findAll { VersionNumber.parse(it) >= VersionNumber.parse(FIXED_ICLASS_LISTENER) }
    static final Set<String> SUPPORTS_DRY_RUN = SUPPORTED_BY_JDK.findAll { VersionNumber.parse(it) >= VersionNumber.parse('6.14') }

    static boolean providesClassListener(Object version) {
        VersionNumber.parse(version.toString()) >= VersionNumber.parse(FIXED_ICLASS_LISTENER)
    }

    private static Set<String> testNgVersionsSupportedByJdk(Set<String> versions, JavaVersion javaVersion) {
        if (javaVersion >= JavaVersion.VERSION_16) {
            return versions.findAll { VersionNumber.parse(it) >= VersionNumber.parse(FIXED_ILLEGAL_ACCESS) }
        } else if (javaVersion < JavaVersion.VERSION_1_7) {
            // 6.8.21 was the last version to compile to JDK 5 bytecode. Afterwards (6.9.4) TestNG compiled to JDK 7 bytecode.
            return versions.findAll { VersionNumber.parse(it) <= VersionNumber.parse('6.8.21')}
        } else {
            return versions
        }
    }

    /**
     * Adds java plugin and configures TestNG support in given build script file.
     */
    static void enableTestNG(File buildFile, version = NEWEST) {
        buildFile << """
            apply plugin: 'java'
            ${RepoScriptBlockUtil.mavenCentralRepository()}
            testing {
                suites {
                    test {
                        useTestNG('${version}')
                    }
                }
            }
        """
    }
}
