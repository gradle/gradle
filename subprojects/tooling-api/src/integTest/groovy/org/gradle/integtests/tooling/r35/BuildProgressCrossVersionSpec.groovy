/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r35

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.gradle.tooling.ProjectConnection
import org.junit.Rule

@ToolingApiVersion(">=2.5")
@TargetGradleVersion(">=3.5")
class BuildProgressCrossVersionSpec extends ToolingApiSpecification {
    public static final String REUSE_USER_HOME_SERVICES = "org.gradle.internal.reuse.user.home.services";

    @Rule public final RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder)

    def "generates events for interleaved project configuration and dependency resolution"() {
        given:
        settingsFile << """
            
            rootProject.name = 'multi'
            include 'a', 'b'
        """
        buildFile << """
            allprojects { apply plugin: 'java' }
            dependencies {
                compile project(':a')
            }
            configurations.compile.each { println it }
"""
        file("a/build.gradle") << """
            dependencies {
                compile project(':b')
            }
            configurations.compile.each { println it }
"""

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        def configureBuild = events.operation("Configure build")

        def configureRoot = events.operation("Configure project :")
        configureRoot.parent == configureBuild
        configureBuild.children.contains(configureRoot)

        def resolveCompile = events.operation("Resolve dependencies :compile")
        def resolveArtifactAinRoot = events.operation(configureRoot, "Resolve artifact a.jar (project :a)")
        def resolveArtifactBinRoot = events.operation(configureRoot, "Resolve artifact b.jar (project :b)")
        resolveCompile.parent == configureRoot
        configureRoot.children == [resolveCompile, resolveArtifactAinRoot, resolveArtifactBinRoot]

        def configureA = events.operation("Configure project :a")
        configureA.parent == resolveCompile
        resolveCompile.children == [configureA]

        def resolveCompileA = events.operation("Resolve dependencies :a:compile")
        def resolveArtifactBinA = events.operation(configureA, "Resolve artifact b.jar (project :b)")

        resolveCompileA.parent == configureA
        configureA.children == [resolveCompileA, resolveArtifactBinA]

        def configureB = events.operation("Configure project :b")
        configureB.parent == resolveCompileA
        resolveCompileA.children == [configureB]
    }

    def "generates events for downloading artifacts"() {
        given:
        toolingApi.requireIsolatedUserHome()

        def projectB = mavenHttpRepo.module('group', 'projectB', '1.0').publish()
        def projectC = mavenHttpRepo.module('group', 'projectC', '1.5').publish()
        def projectD = mavenHttpRepo.module('group', 'projectD', '2.0-SNAPSHOT').publish()

        settingsFile << """
            rootProject.name = 'root'
            include 'a'
        """
        buildFile << """
            allprojects {
                apply plugin:'java'
            }
            repositories {
               maven { url '${mavenHttpRepo.uri}' }
            }
            
            dependencies {
                compile project(':a')
                compile "group:projectB:1.0"
                compile "group:projectC:1.+"
                compile "group:projectD:2.0-SNAPSHOT"
            }
            configurations.compile.each { println it }
"""
        when:
        projectB.pom.expectGet()
        projectB.artifact.expectGet()
        projectC.rootMetaData.expectGet()
        projectC.pom.expectGet()
        projectC.artifact.expectGet()

        projectD.pom.expectGet()
        projectD.metaData.expectGet()
        projectD.artifact.expectGet()

        and:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .setJvmArguments("-D${REUSE_USER_HOME_SERVICES}=false")
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        def configureBuild = events.operation("Configure build")

        def configureRoot = events.operation("Configure project :")
        configureRoot.parent == configureBuild
        configureBuild.children.contains(configureRoot)

        def resolveCompile = events.operation("Resolve dependencies :compile")
        def resolveArtifactA = events.operation("Resolve artifact a.jar (project :a)")
        def resolveArtifactB = events.operation("Resolve artifact projectB.jar (group:projectB:1.0)")
        def resolveArtifactC = events.operation("Resolve artifact projectC.jar (group:projectC:1.5)")
        def resolveArtifactD = events.operation("Resolve artifact projectD.jar (group:projectD:2.0-SNAPSHOT)")
        def downloadBMetadata = events.operation("Download http://localhost:${server.port}${projectB.pomPath}")
        def downloadBArtifact = events.operation("Download http://localhost:${server.port}${projectB.artifactPath}")
        def downloadCRootMetadata = events.operation("Download http://localhost:${server.port}/repo/group/projectC/maven-metadata.xml")
        def downloadCPom = events.operation("Download http://localhost:${server.port}${projectC.pomPath}")
        def downloadCArtifact = events.operation("Download http://localhost:${server.port}${projectC.artifactPath}")
        def downloadDPom = events.operation("Download http://localhost:${server.port}${projectD.pomPath}")
        def downloadDMavenMetadata = events.operation("Download http://localhost:${server.port}${projectD.metaDataPath}")
        resolveCompile.parent == configureRoot
        configureRoot.children == [resolveCompile, resolveArtifactA, resolveArtifactB, resolveArtifactC, resolveArtifactD]

        def configureA = events.operation("Configure project :a")
        configureA.parent == resolveCompile
        resolveCompile.children == [configureA, downloadBMetadata, downloadCRootMetadata, downloadCPom, downloadDMavenMetadata, downloadDPom]
        resolveArtifactA.children.isEmpty()
        resolveArtifactB.children == [downloadBArtifact]
        resolveArtifactC.children == [downloadCArtifact]

        cleanup:
        try {
            toolingApi.getDaemons().killAll()
        } catch (RuntimeException ex) {
            //TODO once we figured out why pid from logfile can be null we should remove this again
            LOGGER.warn("Unable to kill daemon(s)", ex);
        }

    }

    MavenHttpRepository getMavenHttpRepo() {
        return new MavenHttpRepository(server, "/repo", mavenRepo)
    }

    MavenFileRepository getMavenRepo(String name = "repo") {
        return new MavenFileRepository(file(name))
    }

}
