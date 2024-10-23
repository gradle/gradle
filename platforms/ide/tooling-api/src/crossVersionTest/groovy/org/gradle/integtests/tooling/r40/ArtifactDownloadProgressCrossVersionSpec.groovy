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

package org.gradle.integtests.tooling.r40

import org.gradle.integtests.tooling.fixture.AbstractHttpCrossVersionSpec
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.Flaky
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion

class ArtifactDownloadProgressCrossVersionSpec extends AbstractHttpCrossVersionSpec {

    @TargetGradleVersion(">=5.7")
    @Flaky(because = "https://github.com/gradle/gradle-private/issues/3638")
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

        def applyRootBuildScript = configureBuild.child("Configure project :").child(applyRootProjectBuildScript())

        def resolveCompileDependencies = events.operation("Resolve dependencies :compileClasspath", "Resolve dependencies of :compileClasspath")
        def resolveCompileFiles = events.operation(resolveConfigurationFiles(":compileClasspath"), resolveConfigurationFiles(":compileClasspath"))
        def resolveB = events.operation("Resolve group:projectB:1.0")
        def resolveD = events.operation("Resolve group:projectD:2.0-SNAPSHOT")
        def resolveArtifactB = events.operation("Resolve projectB-1.0.jar (group:projectB:1.0)")
        def resolveArtifactC = events.operation("Resolve projectC-1.5.jar (group:projectC:1.5)")
        def resolveArtifactD = events.operation("Resolve projectD-2.0-SNAPSHOT.jar (group:projectD:2.0-SNAPSHOT:20100101.120001-1)")
        def downloadBMetadata = events.operation("Download ${projectB.pom.uri}")
        def downloadBArtifact = events.operation("Download ${projectB.artifact.uri}")
        def downloadCRootMetadata = events.operation("Download ${projectC.rootMetaData.uri}")
        def downloadCPom = events.operation("Download ${projectC.pom.uri}")
        def downloadCArtifact = events.operation("Download ${projectC.artifact.uri}")
        def downloadDPom = events.operation("Download ${projectD.pom.uri}")
        def downloadDMavenMetadata = events.operation("Download ${projectD.metaData.uri}")
        def downloadDArtifact = events.operation("Download ${projectD.artifact.uri}")

        resolveCompileDependencies.parent == applyRootBuildScript
        def configureA = events.operation("Configure project :a")
        configureA.parent == resolveCompileDependencies
        resolveB.parent == resolveCompileDependencies
        downloadCRootMetadata.parent == resolveCompileDependencies
        downloadCPom.parent == resolveCompileDependencies
        resolveD.parent == resolveCompileDependencies

        resolveB.children == [downloadBMetadata]
        resolveD.children == [downloadDMavenMetadata, downloadDPom]

        resolveCompileFiles.parent == applyRootBuildScript
        resolveCompileFiles.children.size() == 3 // resolution happens in parallel, so the operations may appear in any order
        resolveCompileFiles.children.contains(resolveArtifactB)
        resolveCompileFiles.children.contains(resolveArtifactC)
        resolveCompileFiles.children.contains(resolveArtifactD)

        resolveArtifactB.children == [downloadBArtifact]
        resolveArtifactC.children == [downloadCArtifact]
        resolveArtifactD.children == [downloadDArtifact]
    }

    private String applyRootProjectBuildScript() {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "Apply build file 'build.gradle' to root project 'root'"
        } else {
            return "Apply script build.gradle to root project 'root'"
        }
    }

    private String resolveConfigurationFiles(String path) {
        if (targetVersion.baseVersion >= GradleVersion.version("8.7")) {
            return "Resolve files of configuration '" + path + "'"
        } else {
            return "Resolve files of " + path
        }
    }
}
