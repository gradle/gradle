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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
/**
 * Tests for resolving dependency graph with substitution within a composite build.
 */
class CompositeBuildCommandLineIntegrationSpec extends AbstractIntegrationSpec {
    ProjectTestFile buildA
    ProjectTestFile buildB
    MavenFileRepository mavenRepo
    ResolveTestFixture resolve
    def buildArgs = []

    def setup() {
        mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("org.test", "buildB", "1.0").publish()

        buildA = multiProjectBuild("buildA", ['a1', 'a2']) {
            buildFile << """
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                allprojects {
                    apply plugin: 'java'
                    configurations { compile }
                }
"""
        }
        resolve = new ResolveTestFixture(buildA.buildFile)

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    version "2.0"

                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                }
"""
        }
    }

    def "substitutes external dependency with root project dependency"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "org.test:buildB:1.0"
            }
"""

        when:
        executer.withArguments("--participant", "../buildB")
        checkDependencies()

        then:
        checkGraph {
            edge("org.test:buildB:1.0", "project buildB::", "org.test:buildB:2.0") {
                compositeSubstitute()
            }
        }
    }

    private void withArgs(List<String> args) {
        buildArgs = args as List
    }

    private void checkDependencies() {
        resolve.prepare()

        executer.inDirectory(buildA).withTasks(":checkDeps").run()
    }

    void checkGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.expectGraph {
            root(":", "org.test:buildA:1.0", closure)
        }
    }

    TestFile getRootDir() {
        temporaryFolder.testDirectory
    }

    def populate(String projectName, @DelegatesTo(ProjectTestFile) Closure cl) {
        def project = new ProjectTestFile(rootDir, projectName)
        project.with(cl)
        project
    }

    def singleProjectBuild(String projectName, @DelegatesTo(ProjectTestFile) Closure cl = {}) {
        def project = populate(projectName) {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
            """

            buildFile << """
                group = 'org.test'
                version = '1.0'
            """
            file('src/main/java/Dummy.java') << "public class Dummy {}"
        }
        project.with(cl)
        return project
    }

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(ProjectTestFile) Closure cl = {}) {
        String subprojectList = subprojects.collect({"'$it'"}).join(',')
        def rootMulti = populate(projectName) {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
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

    static class ProjectTestFile extends TestFile {
        private final String projectName

        ProjectTestFile(TestFile rootDir, String projectName) {
            super(rootDir, [ projectName ])
            this.projectName = projectName
        }
        String getRootProjectName() {
            projectName
        }
        TestFile getBuildFile() {
            file("build.gradle")
        }
        TestFile getSettingsFile() {
            file("settings.gradle")
        }
        void addChildDir(String name) {
            file(name).file("build.gradle") << "// Dummy child build"
        }
    }

}
