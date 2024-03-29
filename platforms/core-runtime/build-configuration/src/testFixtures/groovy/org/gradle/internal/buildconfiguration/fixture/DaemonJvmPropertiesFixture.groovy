/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.buildconfiguration.fixture

import org.gradle.test.fixtures.file.TestFile

class DaemonJvmPropertiesFixture extends BuildPropertiesFixture {

    DaemonJvmPropertiesFixture(TestFile projectDirectory) {
        super(projectDirectory)
    }

    def assertJvmCriteria(Integer version, String vendor = null, String implementation = null) {
        assertBuildPropertyExist("daemon.jvm.toolchain.version=$version")
        if (vendor == null) {
            assertBuildPropertyNotExist("daemon.jvm.toolchain.vendor")
        } else {
            assertBuildPropertyExist("daemon.jvm.toolchain.vendor=$vendor")
        }
        if (implementation == null) {
            assertBuildPropertyNotExist("daemon.jvm.toolchain.implementation")
        } else {
            assertBuildPropertyExist("daemon.jvm.toolchain.implementation=$implementation")
        }

        return true
    }
}
