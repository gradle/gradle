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

import org.gradle.internal.buildconfiguration.BuildPropertiesDefaults
import org.gradle.test.fixtures.file.TestFile

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

abstract class BuildPropertiesFixture {

    protected final File propertiesFile

    BuildPropertiesFixture(TestFile projectDirectory) {
        propertiesFile = new File(projectDirectory, BuildPropertiesDefaults.BUILD_PROPERTIES_FILE)
    }

    def assertBuildPropertyExist(String property) {
        assertTrue(propertiesFile.text.contains(property))
        return true
    }

    def assertBuildPropertyNotExist(String property) {
        assertFalse(propertiesFile.text.contains(property))
        return true
    }
}
