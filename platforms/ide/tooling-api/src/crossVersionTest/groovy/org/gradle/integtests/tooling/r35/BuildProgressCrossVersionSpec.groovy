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

import org.gradle.integtests.tooling.fixture.AbstractHttpCrossVersionSpec
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import spock.lang.Issue

@TargetGradleVersion(">=3.5")
class BuildProgressCrossVersionSpec extends AbstractHttpCrossVersionSpec {

    @TargetGradleVersion(">=3.5 <4.0")
    def "generates events for interleaved project configuration and dependency resolution"() {
        given:
        settingsFile << """

            rootProject.name = 'multi'
            include 'a', 'b'
        """
        buildFile << """
            allprojects { apply plugin: 'java-library' }
            dependencies {
                api project(':a')
            }
            configurations.runtimeClasspath.each { println it }
"""
        file("a/build.gradle") << """
            dependencies {
                api project(':b')
            }
            configurations.runtimeClasspath.each { println it }
"""

        when:
        def events = ProgressEvents.create()
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

        def resolveCompile = events.operation("Resolve dependencies :runtimeClasspath", "Resolve dependencies of :runtimeClasspath")
        def resolveArtifactAinRoot = events.operation(configureRoot, "Resolve artifact a.jar (project :a)")
        def resolveArtifactBinRoot = events.operation(configureRoot, "Resolve artifact b.jar (project :b)")
        resolveCompile.parent == configureRoot
        configureRoot.children == [resolveCompile, resolveArtifactAinRoot, resolveArtifactBinRoot]

        def configureA = events.operation("Configure project :a")
        configureA.parent == resolveCompile
        resolveCompile.children == [configureA]

        def resolveCompileA = events.operation("Resolve dependencies :a:runtimeClasspath", "Resolve dependencies of :a:runtimeClasspath")
        def resolveArtifactBinA = events.operation(configureA, "Resolve artifact b.jar (project :b)")

        resolveCompileA.parent == configureA
        configureA.children == [resolveCompileA, resolveArtifactBinA]

        def configureB = events.operation("Configure project :b")
        configureB.parent == resolveCompileA
        resolveCompileA.children == [configureB]
    }

    @TargetGradleVersion(">=3.5 <4.0")
    def "generates events for downloading artifacts"() {
        given:
        def modules = setupBuildWithArtifactDownloadDuringConfiguration()

        def projectB = modules.projectB
        def projectC = modules.projectC
        def projectD = modules.projectD

        when:
        def events = ProgressEvents.create()
        withConnection { ProjectConnection connection ->
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

        def resolveCompile = events.operation("Resolve dependencies :compileClasspath", "Resolve dependencies of :compileClasspath")
        def resolveArtifactA = events.operation("Resolve artifact main (project :a)")
        def resolveArtifactB = events.operation("Resolve artifact projectB.jar (group:projectB:1.0)")
        def resolveArtifactC = events.operation("Resolve artifact projectC.jar (group:projectC:1.5)")
        def resolveArtifactD = events.operation("Resolve artifact projectD.jar (group:projectD:2.0-SNAPSHOT)")
        def downloadBMetadata = events.operation("Download ${server.uri}${projectB.pomPath}")
        def downloadBArtifact = events.operation("Download ${server.uri}${projectB.artifactPath}")
        def downloadCRootMetadata = events.operation("Download ${server.uri}/repo/group/projectC/maven-metadata.xml")
        def downloadCPom = events.operation("Download ${server.uri}${projectC.pomPath}")
        def downloadCArtifact = events.operation("Download ${server.uri}${projectC.artifactPath}")
        def downloadDPom = events.operation("Download ${server.uri}${projectD.pomPath}")
        def downloadDMavenMetadata = events.operation("Download ${server.uri}${projectD.metaDataPath}")
        def downloadDArtifact = events.operation("Download ${server.uri}${projectD.artifactPath}")
        resolveCompile.parent == configureRoot
        configureRoot.children == [resolveCompile, resolveArtifactA, resolveArtifactB, resolveArtifactC, resolveArtifactD]

        def configureA = events.operation("Configure project :a")
        configureA.parent == resolveCompile
        resolveCompile.children == [configureA, downloadBMetadata, downloadCRootMetadata, downloadCPom, downloadDMavenMetadata, downloadDPom]
        resolveArtifactA.children.isEmpty()
        resolveArtifactB.children == [downloadBArtifact]
        resolveArtifactC.children == [downloadCArtifact]
        resolveArtifactD.children == [downloadDArtifact]
    }

    @Issue("gradle/gradle#1641")
    @TargetGradleVersion(">=3.5 <4.0")
    def "generates download events during maven publish"() {
        given:
        toolingApi.requireIsolatedUserHome()
        if (targetDist.version.version == "3.5-rc-1") {
            return
        }
        def module = mavenHttpRepo.module('group', 'publish', '1')

        module.withoutExtraChecksums()

        // module is published
        module.publish()

        // module will be published a second time via 'maven-publish'
        module.artifact.expectPublish(false)
        module.pom.expectPublish(false)
        module.rootMetaData.expectGet()
        module.rootMetaData.sha1.expectGet()
        module.rootMetaData.expectGet()
        module.rootMetaData.sha1.expectGet()
        module.rootMetaData.expectPublish(false)

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'
            version = '1'
            group = 'group'

            publishing {
                repositories {
                    maven { url "${mavenHttpRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        def events = ProgressEvents.create()

        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('publish')
                    .addProgressListener(events).run()
        }

        then:
        def roots = events.operations.findAll { it.parent == null }.collect { it.descriptor.name }

        roots == [
            "Run build",
            "Download ${module.rootMetaData.uri}",
            "Download ${module.rootMetaData.sha1.uri}",
            "Download ${module.rootMetaData.uri}",
            "Download ${module.rootMetaData.sha1.uri}"
        ]
    }

    def "generate events for task actions"() {
        given:
        settingsFile << "rootProject.name = 'single'"
        buildFile << 'apply plugin:"java"'
        file("src/main/java/Thing.java") << """class Thing { }"""

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .forTasks('compileJava')
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        and:
        def compileJavaActions = events.operations.findAll { it.descriptor.displayName.matches('Execute .*( action [0-9]+/[0-9]+)? for :compileJava') }
        compileJavaActions.size() > 0
        compileJavaActions[0].hasAncestor { it.descriptor.displayName == 'Task :compileJava' }
    }

    @TargetGradleVersion('>=3.5 <5.1')
    def "generates events for worker actions (pre 5.1)"() {
        expect:
        runBuildWithWorkerRunnable() != null
    }

    @ToolingApiVersion('>=5.1')
    @TargetGradleVersion('>=3.5 <7.0')
    def "generates events for worker actions with old Worker API (post 5.1)"() {
        expect:
        runBuildWithWorkerRunnable() != null
    }

    @ToolingApiVersion('>=5.1')
    @TargetGradleVersion('>=5.6')
    def "generates events for worker actions with new Worker API (post 5.1)"() {
        expect:
        runBuildWithWorkerAction() != null
    }

    private ProgressEvents.Operation runBuildWithWorkerAction() {
        buildFile << """
            import org.gradle.workers.*
            abstract class MyWorkerAction implements WorkAction<WorkParameters.None>{
                @Override public void execute() {
                    // Do nothing
                }
            }
            task runInWorker {
                doLast {
                    def workerExecutor = services.get(WorkerExecutor)
                    workerExecutor.noIsolation().submit(MyWorkerAction) { }
                }
            }
        """

        runBuildInAction()
    }

    private ProgressEvents.Operation runBuildWithWorkerRunnable() {
        buildFile << """
            import org.gradle.workers.*
            class TestRunnable implements Runnable {
                @Override public void run() {
                    // Do nothing
                }
            }
            task runInWorker {
                doLast {
                    def workerExecutor = services.get(WorkerExecutor)
                    workerExecutor.submit(TestRunnable) { config ->
                        config.displayName = 'MyWorkerAction'
                    }
                }
            }
        """

        runBuildInAction()
    }

    private ProgressEvents.Operation runBuildInAction() {
        settingsFile << "rootProject.name = 'single'"

        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .forTasks('runInWorker')
                    .addProgressListener(events)
                    .run()
        }

        events.assertIsABuild()
        events.operation('Task :runInWorker').descendant('MyWorkerAction')
    }
}
