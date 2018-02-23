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

package org.gradle.integtests.fixtures.build

import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile

class BuildTestFixture {
    private final TestDirectoryProvider provider
    private final TestFile rootDir
    private boolean buildInRootDir = true

    BuildTestFixture(TestDirectoryProvider provider) {
        this.provider = provider
        this.rootDir = null
    }

    BuildTestFixture(TestFile rootDir) {
        this.provider = null
        this.rootDir = rootDir
    }

    TestFile getRootDir() {
        return rootDir ?: provider.testDirectory
    }

    BuildTestFixture withBuildInRootDir() {
        buildInRootDir = true
        this
    }

    BuildTestFixture withBuildInSubDir() {
        buildInRootDir = false
        this
    }

    def populate(String projectName, @DelegatesTo(BuildTestFile) Closure cl) {
        def project = buildInRootDir ? new BuildTestFile(getRootDir(), projectName) : new BuildTestFile(getRootDir().file(projectName), projectName)
        project.settingsFile << """
                    rootProject.name = '${projectName}'
                """
        project.with(cl)
        project
    }

    def singleProjectBuild(String projectName, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        def project = populate(projectName) {
            buildFile << """
                    group = 'org.test'
                    version = '1.0'
                """
            file('src/main/java/Dummy.java') << "public class Dummy {}"
        }
        project.with(cl)
        return project
    }

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        String subprojectList = subprojects.collect({ "'$it'" }).join(',')
        def rootMulti = populate(projectName) {
            settingsFile << """
                    include ${subprojectList}
                """

            buildFile << """
                    allprojects {
                        group = 'org.test'
                        version = '1.0'
                    }
                """
        }
        rootMulti.with(cl)
        rootMulti.file('src/main/java/Dummy.java') << "public class Dummy {}"
        subprojects.each {
            rootMulti.file(it, 'src/main/java/Dummy.java') << "public class Dummy {}"
        }
        return rootMulti
    }

}
