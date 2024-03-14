/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.fixtures.build

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.file.TestFile

@CompileStatic
trait TestProjectFilesFixture {

    abstract TestFile file(Object... path)

    abstract void ensureSupportsKotlinDsl()

    TestFile getBuildFile() {
        file('build.gradle')
    }

    TestFile getBuildFileKts() {
        file(buildFileKtsName)
    }

    TestFile getSettingsFile() {
        file('settings.gradle')
    }

    TestFile getSettingsFileKts() {
        file(settingsFileKtsName)
    }

    TestFile getPropertiesFile() {
        file("gradle.properties")
    }

    String getBuildFileKtsName() {
        ensureSupportsKotlinDsl()
        'build.gradle.kts'
    }

    String getSettingsFileKtsName() {
        ensureSupportsKotlinDsl()
        'settings.gradle.kts'
    }

    List<TestFile> createDirs(String... dirs) {
        dirs.collect({ name ->
            TestFile tf = file(name)
            tf.mkdirs()
            return tf
        })
    }

}
