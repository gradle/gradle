/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixture

import org.gradle.integtests.fixtures.MavenFileRepository
import org.gradle.util.TestFile
import org.gradle.integtests.fixtures.MavenRepository

class M2Installation {
    final TestFile userM2Directory
    final TestFile userSettingsFile
    TestFile globalMavenDirectory = null;

    public M2Installation(TestFile m2Directory) {
        this.userM2Directory = m2Directory;
        this.userSettingsFile = m2Directory.file("settings.xml")
    }

    MavenRepository mavenRepo() {
        new MavenFileRepository(userM2Directory.file("repository"))
    }

    TestFile createGlobalSettingsFile(TestFile globalMavenDirectory) {
        this.globalMavenDirectory = globalMavenDirectory;
        globalMavenDirectory.file("conf/settings.xml").createFile()
    }
}
