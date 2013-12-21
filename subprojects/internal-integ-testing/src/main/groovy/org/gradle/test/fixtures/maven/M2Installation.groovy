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



package org.gradle.test.fixtures.maven

import org.gradle.api.Action
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestFile

class M2Installation implements Action<GradleExecuter> {
    final TestFile userHomeDir
    final TestFile userM2Directory
    final TestFile userSettingsFile
    final TestFile globalMavenDirectory
    final TestFile globalSettingsFile

    public M2Installation(TestFile m2Directory) {
        userHomeDir = m2Directory.createDir("maven_home")
        userM2Directory = userHomeDir.createDir(".m2")
        userSettingsFile = userM2Directory.file("settings.xml")
        globalMavenDirectory = userHomeDir.createDir("m2_home")
        globalSettingsFile = globalMavenDirectory.file("conf/settings.xml")
    }

    MavenLocalRepository mavenRepo() {
        new MavenLocalRepository(userM2Directory.file("repository"))
    }

    M2Installation generateUserSettingsFile(MavenLocalRepository userRepository) {
        userSettingsFile.text = """
<settings>
    <localRepository>${userRepository.rootDir.absolutePath}</localRepository>
</settings>"""
        return this
    }

    M2Installation generateGlobalSettingsFile(MavenLocalRepository globalRepository = mavenRepo()) {
        globalSettingsFile.createFile().text = """
<settings>
    <localRepository>${globalRepository.rootDir.absolutePath}</localRepository>
</settings>"""
        return this
    }

    void execute(GradleExecuter executer) {
        executer.withUserHomeDir(userHomeDir)
        if (globalMavenDirectory?.exists()) {
            executer.withEnvironmentVars(M2_HOME:globalMavenDirectory.absolutePath)
        }
    }
}
