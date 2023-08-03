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
package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.util.internal.TextUtil
import org.junit.Rule

class SamplesIvyPublishIntegrationTest extends AbstractSampleIntegrationTest {
    @Rule
    public final Sample sampleProject = new Sample(temporaryFolder)

    @UsesSample("ivy-publish/quickstart")
    def "quickstart sample with #dsl dsl"() {
        given:
        def sampleDir = sampleProject.dir.file(dsl)
        inDirectory(sampleDir)

        and:
        def fileRepo = ivy(sampleDir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "quickstart", "1.0").withModuleMetadata()

        when:
        succeeds "publish"

        then:
        module.assertPublishedAsJavaModule()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("ivy-publish/java-multi-project")
    def "java-multi-project sample with #dsl dsl"() {
        given:
        def sampleDir = sampleProject.dir.file(dsl)
        inDirectory(sampleDir)

        and:
        def fileRepo = ivy(sampleDir.file("build/repo"))
        def project1module = fileRepo.module("org.gradle.sample", "project1", "1.0").withModuleMetadata()
        def project2module = fileRepo.module("org.gradle.sample", "project2", "1.0").withModuleMetadata()

        when:
        succeeds "publish"

        then:
        project1module.assertPublished()
        project1module.assertArtifactsPublished("project1-1.0.jar", "project1-1.0-javadoc.jar", "project1-1.0-sources.jar", "ivy-1.0.xml", "project1-1.0.module")

        project1module.parsedIvy.configurations.keySet() == ['default', 'compile', 'runtime', 'javadocElements', 'sourcesElements'] as Set
        project1module.parsedIvy.description.text() == "The first project"
        project1module.parsedIvy.assertDependsOn("junit:junit:4.13@runtime", "org.gradle.sample:project2:1.0@runtime")

        and:
        project2module.assertPublished()
        project2module.assertArtifactsPublished("project2-1.0.jar", "project2-1.0-javadoc.jar", "project2-1.0-sources.jar", "ivy-1.0.xml", "project2-1.0.module")

        project2module.parsedIvy.configurations.keySet() == ['default', 'compile', 'runtime', 'javadocElements', 'sourcesElements'] as Set
        project2module.parsedIvy.description.text() == "The second project"
        project2module.parsedIvy.assertDependsOn('commons-collections:commons-collections:3.2.2@runtime')

        def actualIvyXmlText = project1module.ivyFile.text.replaceFirst('publication="\\d+"', 'publication="«PUBLICATION-TIME-STAMP»"').trim()
        actualIvyXmlText == getExpectedIvyOutput(sampleProject.dir.file("output-ivy.xml"))

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("ivy-publish/descriptor-customization")
    def "descriptor-customization sample with #dsl dsl"() {
        given:
        def sampleDir = sampleProject.dir.file(dsl)
        inDirectory(sampleDir)

        and:
        def fileRepo = ivy(sampleDir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "descriptor-customization", "1.0").withModuleMetadata()

        when:
        succeeds "publish"

        then:
        module.assertPublished()
        with(module.parsedIvy) {
            licenses[0].@name == 'The Apache License, Version 2.0'
            licenses[0].@url == 'http://www.apache.org/licenses/LICENSE-2.0.txt'
            authors[0].@name == 'Jane Doe'
            authors[0].@url == 'http://example.com/users/jane'
            description.text() == "A concise description of my library"
            description.@homepage == 'http://www.example.com/library'
        }
        sampleDir.file("build/generated-ivy.xml").assertExists()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("ivy-publish/conditional-publishing/groovy")
    def conditionalPublishing() {
        given:
        sample sampleProject

        and:
        def artifactId = "maven-conditional-publishing"
        def version = "1.0"
        def externalRepo = ivy(sampleProject.dir.file("build/repos/external"))
        def binary = externalRepo.module("org.gradle.sample", artifactId, version).withModuleMetadata()
        def internalRepo = ivy(sampleProject.dir.file("build/repos/internal"))
        def binaryAndSources = internalRepo.module("org.gradle.sample", artifactId, version).withModuleMetadata()

        when:
        succeeds "publish"

        then:
        executed ":publishBinaryAndSourcesPublicationToInternalRepository", ":publishBinaryPublicationToExternalRepository"
        skipped ":publishBinaryAndSourcesPublicationToExternalRepository", ":publishBinaryPublicationToInternalRepository"

        and:
        binary.assertPublishedAsJavaModule()
        binaryAndSources.assertPublished()
        binaryAndSources.assertArtifactsPublished "${artifactId}-${version}.jar", "${artifactId}-${version}-sources.jar", "ivy-${version}.xml", "${artifactId}-${version}.module"
    }

    @UsesSample("ivy-publish/conditional-publishing")
    def shorthandPublishToExternalRepository() {
        given:
        inDirectory(sampleProject.dir.file('groovy'))

        when:
        succeeds "publishToExternalRepository"

        then:
        executed ":publishBinaryPublicationToExternalRepository"
        skipped ":publishBinaryAndSourcesPublicationToExternalRepository"
        notExecuted ":publishBinaryPublicationToInternalRepository", ":publishBinaryAndSourcesPublicationToInternalRepository"
    }

    @UsesSample("ivy-publish/conditional-publishing")
    def shorthandPublishToInternalRepository() {
        given:
        inDirectory(sampleProject.dir.file('groovy'))

        when:
        succeeds "publishToInternalRepository"

        then:
        executed ":publishBinaryAndSourcesPublicationToInternalRepository"
        skipped ":publishBinaryPublicationToInternalRepository"
        notExecuted ":publishBinaryPublicationToExternalRepository", ":publishBinaryAndSourcesPublicationToExternalRepository"
    }

    @UsesSample("ivy-publish/publish-artifact/groovy")
    def publishesRpmArtifact() {
        given:
        sample sampleProject
        def artifactId = "publish-artifact"
        def version = "1.0"
        def repo = ivy(sampleProject.dir.file("build/repo"))
        def module = repo.module("org.gradle.sample", artifactId, version).withModuleMetadata()

        when:
        succeeds "publish"

        then:
        executed ":rpm", ":publish"

        and:
        module.assertPublished()
        module.assertArtifactsPublished "${artifactId}-${version}.rpm", "ivy-${version}.xml"
    }

    @UsesSample("ivy-publish/distribution")
    def "publishes distribution archives with #dsl dsl"() {
        given:
        def sampleDir = sampleProject.dir.file(dsl)
        executer.inDirectory(sampleDir)

        and:
        def repo = ivy(sampleDir.file("build/repo"))
        def artifactId = "distribution"
        def version = "1.0"
        def module = repo.module("org.gradle.sample", artifactId, version).withModuleMetadata()

        when:
        succeeds "publish"

        then:
        executed ":customDistTar", ":distZip"

        and:
        module.assertPublished()
        module.assertArtifactsPublished "${artifactId}-${version}.zip", "${artifactId}-${version}.tar", "ivy-${version}.xml"

        where:
        dsl << ['groovy', 'kotlin']
    }

    String getExpectedIvyOutput(File outputFile) {
        assert outputFile.file
        outputFile.readLines()[1..-1].join(TextUtil.getPlatformLineSeparator()).trim()
    }
}
