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


package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Unroll

class SamplesMavenPublishIntegrationTest extends AbstractSampleIntegrationTest {
    @Rule public final Sample sampleProject = new Sample(temporaryFolder)

    @UsesSample("maven-publish/quickstart")
    def quickstartPublish() {
        given:
        sample sampleProject

        and:
        def fileRepo = maven(sampleProject.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "quickstart", "1.0")

        when:
        succeeds "publish"

        then:
        def pom = module.parsedPom
        module.assertPublishedAsJavaModule()
        pom.scopes.isEmpty()
    }

    @UsesSample("maven-publish/quickstart")
    def quickstartPublishLocal() {
        using m2
        
        given:
        executer.beforeExecute m2
        def localModule = m2.mavenRepo().module("org.gradle.sample", "quickstart", "1.0")

        and:
        sample sampleProject

        and:
        def fileRepo = maven(sampleProject.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "quickstart", "1.0")

        when:
        succeeds 'publishToMavenLocal'

        then: "jar is published to maven local repository"
        module.assertNotPublished()
        localModule.assertPublishedAsJavaModule()
    }

    @UsesSample("maven-publish/javaProject")
    def javaProject() {
        given:
        sample sampleProject

        and:
        def fileRepo = maven(sampleProject.dir.file("build/repos/releases"))
        def module = fileRepo.module("org.gradle.sample", "javaProject", "1.0")

        when:
        succeeds "publish"

        then:
        module.assertPublished()
        module.assertArtifactsPublished(
            "javaProject-1.0.jar",
            "javaProject-1.0-sources.jar",
            "javaProject-1.0-javadoc.jar",
            "javaProject-1.0.pom")
        module.parsedPom.packaging == null
        module.parsedPom.scopes.compile.assertDependsOn("commons-collections:commons-collections:3.2.2")
    }

    @UsesSample("maven-publish/multiple-publications")
    def multiplePublications() {
        given:
        sample sampleProject

        and:
        def fileRepo = maven(sampleProject.dir.file("build/repo"))
        def project1sample = fileRepo.module("org.gradle.sample", "project1-sample", "1.1")
        def project2api = fileRepo.module("org.gradle.sample", "project2-api", "2.3")
        def project2impl = fileRepo.module("org.gradle.sample", "project2-impl", "2.3")

        when:
        succeeds "publish"

        then:
        project1sample.assertPublishedAsJavaModule()
        verifyPomFile(project1sample, "output/project1.pom.xml")

        and:
        project2api.assertPublishedAsJavaModule()
        verifyPomFile(project2api, "output/project2-api.pom.xml")

        and:
        project2impl.assertPublishedAsJavaModule()
        verifyPomFile(project2impl, "output/project2-impl.pom.xml")
    }

    @UsesSample("maven-publish/conditional-publishing")
    def conditionalPublishing() {
        using m2

        given:
        sample sampleProject

        and:
        def artifactId = "maven-conditional-publishing"
        def version = "1.0"
        def externalRepo = maven(sampleProject.dir.file("build/repos/external"))
        def binary = externalRepo.module("org.gradle.sample", artifactId, version)
        def internalRepo = maven(sampleProject.dir.file("build/repos/internal"))
        def binaryAndSourcesInRepo = internalRepo.module("org.gradle.sample", artifactId, version)
        def localRepo = maven(temporaryFolder.createDir("m2_repo"))
        def binaryAndSourcesLocal = localRepo.module("org.gradle.sample", artifactId, version)

        when:
        args "-Dmaven.repo.local=${localRepo.rootDir.getAbsolutePath()}"
        succeeds "publish", "publishToMavenLocal"

        then:
        executed ":publishBinaryAndSourcesPublicationToInternalRepository", ":publishBinaryPublicationToExternalRepository", ":publishBinaryAndSourcesPublicationToMavenLocal"
        skipped ":publishBinaryAndSourcesPublicationToExternalRepository", ":publishBinaryPublicationToInternalRepository", ":publishBinaryPublicationToMavenLocal"

        and:
        binary.assertPublishedAsJavaModule()
        binaryAndSourcesInRepo.assertPublished()
        binaryAndSourcesInRepo.assertArtifactsPublished "${artifactId}-${version}.jar", "${artifactId}-${version}-sources.jar", "${artifactId}-${version}.pom"
        binaryAndSourcesLocal.assertPublished()
    }

    @UsesSample("maven-publish/conditional-publishing")
    def shorthandPublishToExternalRepository() {
        given:
        sample sampleProject

        when:
        succeeds "publishToExternalRepository"

        then:
        executed ":publishBinaryPublicationToExternalRepository"
        skipped ":publishBinaryAndSourcesPublicationToExternalRepository"
        notExecuted ":publishBinaryPublicationToInternalRepository", ":publishBinaryAndSourcesPublicationToInternalRepository"
    }

    @UsesSample("maven-publish/conditional-publishing")
    def shorthandPublishForDevelopment() {
        given:
        sample sampleProject
        def localRepo = temporaryFolder.createDir("m2_repo")

        when:
        args "-Dmaven.repo.local=${localRepo.getAbsolutePath()}"
        succeeds "publishForDevelopment"

        then:
        executed ":publishBinaryAndSourcesPublicationToInternalRepository", ":publishBinaryAndSourcesPublicationToMavenLocal"
        skipped ":publishBinaryPublicationToInternalRepository", ":publishBinaryPublicationToMavenLocal"
        notExecuted ":publishBinaryPublicationToExternalRepository", ":publishBinaryAndSourcesPublicationToExternalRepository"
    }

    @UsesSample("maven-publish/publish-artifact")
    def publishesRpmArtifact() {
        given:
        sample sampleProject
        def artifactId = "publish-artifact"
        def version = "1.0"
        def repo = maven(sampleProject.dir.file("build/repo"))
        def module = repo.module("org.gradle.sample", artifactId, version)

        when:
        succeeds "publish"

        then:
        executed ":rpm", ":publish"

        and:
        module.assertPublished()
        module.assertArtifactsPublished "${artifactId}-${version}.rpm", "${artifactId}-${version}.pom"
    }

    @UsesSample("maven-publish/pomGeneration")
    def pomGeneration() {
        given:
        sample sampleProject

        when:
        succeeds "generatePomFileForMavenCustomPublication"

        then:
        def pom = sampleProject.dir.file("build/generated-pom.xml").assertExists()
        def parsedPom = new org.gradle.test.fixtures.maven.MavenPom(pom)
        parsedPom.name == "Example"
    }

    @Unroll
    @UsesSample("maven-publish/distribution")
    def "publishes distribution archives with #dsl dsl"() {
        given:
        def sampleDir = sampleProject.dir.file(dsl)
        executer.inDirectory(sampleDir).requireGradleDistribution()

        and:
        def repo = maven(sampleDir.file("build/repo"))
        def artifactId = "distribution"
        def version = "1.0"
        def module = repo.module("org.gradle.sample", artifactId, version)

        when:
        succeeds "publish"

        then:
        executed ":customDistTar", ":distZip"

        and:
        module.assertPublished()
        module.assertArtifactsPublished "${artifactId}-${version}.zip", "${artifactId}-${version}.tar", "${artifactId}-${version}.pom"

        where:
        dsl << ['groovy', 'kotlin']
    }

    private void verifyPomFile(MavenFileModule module, String outputFileName) {
        def actualPomXmlText = module.pomFile.text.replaceFirst('publication="\\d+"', 'publication="«PUBLICATION-TIME-STAMP»"').trim()
        assert actualPomXmlText == getExpectedPomOutput(sampleProject.dir.file(outputFileName))
    }

    String getExpectedPomOutput(File outputFile) {
        assert outputFile.file
        outputFile.readLines()[1..-1].join(TextUtil.getPlatformLineSeparator()).trim()
    }
}
