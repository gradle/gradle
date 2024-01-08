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

import org.gradle.integtests.fixtures.CompiledLanguage
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

    def populate(String projectName, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl) {
        def project = buildInRootDir ? new BuildTestFile(getRootDir(), projectName) : new BuildTestFile(getRootDir().file(projectName), projectName)
        project.settingsFile << """
                    rootProject.name = '${projectName}'
                """
        project.with(cl)
        project
    }

    def singleProjectBuild(String projectName, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
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

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
        multiProjectBuild(projectName, subprojects, CompiledLanguage.JAVA, cl)
    }

    def multiProjectBuild(String projectName, List<String> subprojects, CompiledLanguage language, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
        def rootMulti = populate(projectName) {
            subprojects.each {
                settingsFile << "include '$it'\n"
            }

            buildFile << """
                    allprojects {
                        group = 'org.test'
                        version = '1.0'
                    }
                """
        }
        rootMulti.with(cl)
        addSourceToAllProjects(rootMulti, language, subprojects)
        return rootMulti
    }

    def multiProjectBuildWithIsolatedProjects(String projectName, List<String> subprojects, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
        multiProjectBuildWithIsolatedProjects(projectName, subprojects, CompiledLanguage.JAVA, cl)
    }

    def multiProjectBuildWithIsolatedProjects(String projectName, List<String> subprojects, CompiledLanguage language, @DelegatesTo(value = BuildTestFile, strategy = Closure.DELEGATE_FIRST) Closure cl = {}) {
        def rootMulti = populate(projectName) {
            subprojects.each {
                settingsFile << "include '$it'\n"

                project(it).buildFile << """
                    group = 'org.test'
                    version = '1.0'
                """
            }

            buildFile << """
                group = 'org.test'
                version = '1.0'
            """
        }

        rootMulti.with(cl)
        addSourceToAllProjects(rootMulti, language, subprojects)
        return rootMulti
    }

    private void addSourceToAllProjects(BuildTestFile rootMulti, CompiledLanguage language, List<String> subprojects) {
        rootMulti.file("src/main/${language.name}/Dummy.${language.name}") << "public class Dummy {}"
        subprojects.each {
            rootMulti.file(it.replace(':' as char, File.separatorChar), "src/main/${language.name}/Dummy.${language.name}") << "public class Dummy {}"
        }
    }

}
