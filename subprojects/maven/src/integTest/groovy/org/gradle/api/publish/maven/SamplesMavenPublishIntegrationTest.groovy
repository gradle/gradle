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
import org.junit.Rule

class SamplesMavenPublishIntegrationTest extends AbstractSampleIntegrationTest {
    @Rule
    public final Sample sampleProject = new Sample(temporaryFolder)

    @UsesSample("maven-publish/quickstart")
    def "quickstart publish with #dsl dsl"() {
        given:
        def sampleDir = sampleProject.dir.file(dsl)
        executer.inDirectory(sampleDir)

        and:
        def fileRepo = maven(sampleDir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "quickstart", "1.0").withModuleMetadata()

        when:
        succeeds "publish"

        then:
        def pom = module.parsedPom
        module.assertPublishedAsJavaModule()
        pom.scopes.isEmpty()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("maven-publish/quickstart")
    def "quickstart publish local with #dsl dsl"() {
        using m2

        given:
        executer.beforeExecute m2
        def localModule = m2.mavenRepo().module("org.gradle.sample", "quickstart", "1.0").withModuleMetadata()

        and:
        def sampleDir = sampleProject.dir.file(dsl)
        executer.inDirectory(sampleDir)

        and:
        def fileRepo = maven(sampleDir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "quickstart", "1.0")

        when:
        succeeds 'publishToMavenLocal'

        then: "jar is published to maven local repository"
        module.assertNotPublished()
        localModule.assertPublishedAsJavaModule()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("maven-publish/javaProject")
    def "publish java project with #dsl dsl"() {
        given:
        def sampleDir = sampleProject.dir.file(dsl)
        executer.inDirectory(sampleDir)

        and:
        def fileRepo = maven(sampleDir.file("build/repos/releases"))
        def module = fileRepo.module("org.gradle.sample", "java-project", "1.0").withModuleMetadata()

        when:
        succeeds "publish"

        then:
        module.assertPublished()
        module.assertArtifactsPublished(
            "java-project-1.0.jar",
            "java-project-1.0-javadoc.jar",
            "java-project-1.0.pom",
            "java-project-1.0.module")
        module.parsedPom.packaging == null
        module.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:3.2.2")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("maven-publish/customize-identity")
    def "customize publication identity with #dsl dsl"() {
        given:
        def sampleDir = sampleProject.dir.file(dsl)
        inDirectory(sampleDir)

        and:
        def fileRepo = maven(sampleDir.file("build/repo"))
        def library = fileRepo.module("org.gradle.sample", "library", "1.1").withModuleMetadata()

        when:
        succeeds "publish"

        then:
        library.assertPublishedAsJavaModule()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("maven-publish/conditional-publishing")
    def "conditional publishing with #dsl dsl"() {
        using m2

        given:
        def sampleDir = sampleProject.dir.file(dsl)
        inDirectory(sampleDir)

        and:
        def artifactId = "maven-conditional-publishing"
        def version = "1.0"
        def externalRepo = maven(sampleDir.file("build/repos/external"))
        def binary = externalRepo.module("org.gradle.sample", artifactId, version).withModuleMetadata()
        def internalRepo = maven(sampleDir.file("build/repos/internal"))
        def binaryAndSourcesInRepo = internalRepo.module("org.gradle.sample", artifactId, version).withModuleMetadata()
        def localRepo = maven(temporaryFolder.createDir("m2_repo"))
        def binaryAndSourcesLocal = localRepo.module("org.gradle.sample", artifactId, version).withModuleMetadata()

        when:
        args "-Dmaven.repo.local=${localRepo.rootDir.getAbsolutePath()}"
        succeeds "publish", "publishToMavenLocal"

        then:
        executed ":publishBinaryAndSourcesPublicationToInternalRepository", ":publishBinaryPublicationToExternalRepository", ":publishBinaryAndSourcesPublicationToMavenLocal"
        skipped ":publishBinaryAndSourcesPublicationToExternalRepository", ":publishBinaryPublicationToInternalRepository", ":publishBinaryPublicationToMavenLocal"

        and:
        binary.assertPublishedAsJavaModule()
        binaryAndSourcesInRepo.assertPublished()
        binaryAndSourcesInRepo.assertArtifactsPublished "${artifactId}-${version}.jar", "${artifactId}-${version}-sources.jar", "${artifactId}-${version}.pom", "${artifactId}-${version}.module"
        binaryAndSourcesLocal.assertPublished()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("maven-publish/conditional-publishing")
    def "shorthand publish to external repository with #dsl dsl"() {
        given:
        inDirectory(sampleProject.dir.file(dsl))

        when:
        succeeds "publishToExternalRepository"

        then:
        executed ":publishBinaryPublicationToExternalRepository"
        skipped ":publishBinaryAndSourcesPublicationToExternalRepository"
        notExecuted ":publishBinaryPublicationToInternalRepository", ":publishBinaryAndSourcesPublicationToInternalRepository"

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("maven-publish/conditional-publishing")
    def "shorthand publish for development with #dsl dsl"() {
        given:
        inDirectory(sampleProject.dir.file(dsl))
        def localRepo = temporaryFolder.createDir("m2_repo")

        when:
        args "-Dmaven.repo.local=${localRepo.getAbsolutePath()}"
        succeeds "publishForDevelopment"

        then:
        executed ":publishBinaryAndSourcesPublicationToInternalRepository", ":publishBinaryAndSourcesPublicationToMavenLocal"
        skipped ":publishBinaryPublicationToInternalRepository", ":publishBinaryPublicationToMavenLocal"
        notExecuted ":publishBinaryPublicationToExternalRepository", ":publishBinaryAndSourcesPublicationToExternalRepository"

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("maven-publish/publish-artifact")
    def "publishes rpm artifact with #dsl dsl"() {
        given:
        def sampleDir = sampleProject.dir.file(dsl)
        inDirectory(sampleDir)
        def artifactId = "publish-artifact"
        def version = "1.0"
        def repo = maven(sampleDir.file("build/repo"))
        def module = repo.module("org.gradle.sample", artifactId, version)

        when:
        succeeds "publish"

        then:
        executed ":rpm", ":publish"

        and:
        module.assertPublished()
        module.assertArtifactsPublished "${artifactId}-${version}.rpm", "${artifactId}-${version}.pom"

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("maven-publish/distribution")
    def "publishes distribution archives with #dsl dsl"() {
        given:
        def sampleDir = sampleProject.dir.file(dsl)
        executer.inDirectory(sampleDir)

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

}
