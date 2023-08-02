/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.tooling.fixture


import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer

abstract class AbstractHttpCrossVersionSpec extends ToolingApiSpecification {

    protected RepositoryHttpServer server

    def setup() {
        server = new RepositoryHttpServer(temporaryFolder, targetDist.version.version)
        server.before()
    }

    def cleanup() {
        server.after()
    }

    MavenHttpRepository getMavenHttpRepo(String contextPath = "/repo") {
        return new MavenHttpRepository(server, contextPath, getMavenRepo(contextPath.substring(1)))
    }

    MavenFileRepository getMavenRepo(String name = "repo") {
        return new MavenFileRepository(file(name))
    }

    Modules setupBuildWithArtifactDownloadDuringConfiguration() {
        Modules modules = setupBuildWithDependencies()
        addConfigurationClassPathPrintToBuildFile()
        modules.expectResolved()
        return modules
    }

    def addConfigurationClassPathPrintToBuildFile() {
        buildFile << """
            configurations.compileClasspath.each { println it }
        """
    }

    Modules setupBuildWithArtifactDownloadDuringTaskExecution() {
        def modules = setupBuildWithDependencies()
        addResolveTask()
        modules.expectResolved()
        return modules
    }

    Modules setupBuildWithFailedArtifactDownloadDuringTaskExecution() {
        def modules = setupBuildWithDependencies()
        addResolveTask()
        modules.expectResolveFailure()
        return modules
    }

    private void addResolveTask() {
        buildFile << """
            task resolve {
                def files = configurations.compileClasspath
                inputs.files files
                doFirst {
                    files.forEach { println(it) }
                }
            }
        """
    }

    def initSettingsFile() {
        settingsFile << """
            rootProject.name = 'root'
            include 'a'
        """
    }

    def repositories(MavenHttpRepository... repositories) {
        """repositories {${
            repositories.collect { "maven { url '${it.uri}' }" }.join("\n")}
        }"""
    }

    Modules setupBuildWithDependencies() {
        toolingApi.requireIsolatedUserHome()

        def projectB = mavenHttpRepo.module('group', 'projectB', '1.0').publish()
        def projectC = mavenHttpRepo.module('group', 'projectC', '1.5').publish()
        def projectD = mavenHttpRepo.module('group', 'projectD', '2.0-SNAPSHOT').publish()
        def modules = new Modules(projectB, projectC, projectD)

        initSettingsFile()

        buildFile << """
            allprojects {
                apply plugin:'java-library'
            }
            ${repositories(mavenHttpRepo)}
            dependencies {
                implementation project(':a')
                implementation "group:projectB:1.0"
                implementation "group:projectC:1.+"
                implementation "group:projectD:2.0-SNAPSHOT"
            }
        """
        return modules
    }

    static class Modules {
        final MavenHttpModule projectB
        final MavenHttpModule projectC
        final MavenHttpModule projectD

        Modules(MavenHttpModule projectB, MavenHttpModule projectC, MavenHttpModule projectD) {
            this.projectB = projectB
            this.projectC = projectC
            this.projectD = projectD
        }

        def useLargeJars() {
            try (def file = new RandomAccessFile(projectB.artifact.file, "rw")) {
                file.setLength(100 * 1024) // not that large
            }
        }

        def expectResolved() {
            projectB.pom.expectGet()
            projectB.artifact.expectGet()

            projectC.rootMetaData.expectGet()
            projectC.pom.expectGet()
            projectC.artifact.expectGet()

            projectD.metaData.expectGet()
            projectD.pom.expectGet()
            projectD.artifact.expectGet()
        }

        def expectResolveFailure() {
            projectB.pom.allowGetOrHead()

            projectC.rootMetaData.expectGet()
            projectC.pom.expectGetBroken()
            projectC.pom.expectGetBroken()
            projectC.pom.expectGetBroken()

            projectD.metaData.allowGetOrHead()
            projectD.pom.allowGetOrHead()
        }
    }
}
