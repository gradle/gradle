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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.util.TextUtil
import org.junit.Rule

class SamplesIvyPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final Sample sampleProject = new Sample(temporaryFolder)

    @UsesSample("ivy-publish/quickstart")
    def "quickstart sample"() {
        given:
        sample sampleProject

        and:
        def fileRepo = ivy(sampleProject.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "quickstart", "1.0")

        when:
        succeeds "publish"

        then:
        module.assertPublishedAsJavaModule()
    }

    @UsesSample("ivy-publish/java-multi-project")
    def "java-multi-project sample"() {
        given:
        sample sampleProject

        and:
        def fileRepo = ivy(sampleProject.dir.file("build/repo"))
        def project1module = fileRepo.module("org.gradle.sample", "project1", "1.0")
        def project2module = fileRepo.module("org.gradle.sample", "project2", "1.0")

        when:
        succeeds "publish"

        then:
        project1module.assertPublished()
        project1module.assertArtifactsPublished("project1-1.0.jar", "project1-1.0-source.jar", "ivy-1.0.xml")

        project1module.parsedIvy.configurations.keySet() == ['default', 'compile', 'runtime'] as Set
        project1module.parsedIvy.description == "The first project"
        project1module.parsedIvy.assertDependsOn("junit:junit:4.12@compile", "org.gradle.sample:project2:1.0@compile")

        and:
        project2module.assertPublished()
        project2module.assertArtifactsPublished("project2-1.0.jar", "project2-1.0-source.jar", "ivy-1.0.xml")

        project2module.parsedIvy.configurations.keySet() == ['default', 'compile', 'runtime'] as Set
        project2module.parsedIvy.description == "The second project"
        project2module.parsedIvy.assertDependsOn('commons-collections:commons-collections:3.2.2@compile')

        def actualIvyXmlText = project1module.ivyFile.text.replaceFirst('publication="\\d+"', 'publication="«PUBLICATION-TIME-STAMP»"').trim()
        actualIvyXmlText == getExpectedIvyOutput(sampleProject.dir.file("output-ivy.xml"))
    }

    @UsesSample("ivy-publish/descriptor-customization")
    def "descriptor-customization sample"() {
        given:
        sample sampleProject

        and:
        def fileRepo = ivy(sampleProject.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "descriptor-customization", "1.0")

        when:
        succeeds "publish"

        then:
        module.assertPublished()
        module.parsedIvy.description == "A demonstration of ivy descriptor customization"
    }

    @UsesSample("ivy-publish/multiple-publications")
    def "multiple-publications sample"() {
        given:
        sample sampleProject

        and:
        def fileRepo = ivy(sampleProject.dir.file("build/repo"))
        def project1sample = fileRepo.module("org.gradle.sample", "project1-sample", "1.1")
        def project2api = fileRepo.module("org.gradle.sample", "project2-api", "2")
        def project2impl = fileRepo.module("org.gradle.sample.impl", "project2-impl", "2.3")

        when:
        succeeds "publish"

        then:
        project1sample.assertPublishedAsJavaModule()
        verifyIvyFile(project1sample, "output/project1.ivy.xml")

        and:
        project2api.assertPublishedAsJavaModule()
        verifyIvyFile(project2api, "output/project2-api.ivy.xml")

        and:
        project2impl.assertPublishedAsJavaModule()
        verifyIvyFile(project2impl, "output/project2-impl.ivy.xml")
    }

    @UsesSample("ivy-publish/conditional-publishing")
    def conditionalPublishing() {
        given:
        sample sampleProject

        and:
        def artifactId = "maven-conditional-publishing"
        def version = "1.0"
        def externalRepo = ivy(sampleProject.dir.file("build/repos/external"))
        def binary = externalRepo.module("org.gradle.sample", artifactId, version)
        def internalRepo = ivy(sampleProject.dir.file("build/repos/internal"))
        def binaryAndSources = internalRepo.module("org.gradle.sample", artifactId, version)

        when:
        succeeds "publish"

        then:
        executed ":publishBinaryAndSourcesPublicationToInternalRepository", ":publishBinaryPublicationToExternalRepository"
        skipped ":publishBinaryAndSourcesPublicationToExternalRepository", ":publishBinaryPublicationToInternalRepository"

        and:
        binary.assertPublishedAsJavaModule()
        binaryAndSources.assertPublished()
        binaryAndSources.assertArtifactsPublished "${artifactId}-${version}.jar", "${artifactId}-${version}-sources.jar", "ivy-${version}.xml"
    }

    @UsesSample("ivy-publish/conditional-publishing")
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

    @UsesSample("ivy-publish/conditional-publishing")
    def shorthandPublishToInternalRepository() {
        given:
        sample sampleProject

        when:
        succeeds "publishToInternalRepository"

        then:
        executed ":publishBinaryAndSourcesPublicationToInternalRepository"
        skipped ":publishBinaryPublicationToInternalRepository"
        notExecuted ":publishBinaryPublicationToExternalRepository", ":publishBinaryAndSourcesPublicationToExternalRepository"
    }

    private void verifyIvyFile(IvyFileModule project1sample, String outputFileName) {
        def actualIvyXmlText = project1sample.ivyFile.text.replaceFirst('publication="\\d+"', 'publication="«PUBLICATION-TIME-STAMP»"').trim()
        assert actualIvyXmlText == getExpectedIvyOutput(sampleProject.dir.file(outputFileName))
    }

    String getExpectedIvyOutput(File outputFile) {
        assert outputFile.file
        outputFile.readLines()[1..-1].join(TextUtil.getPlatformLineSeparator()).trim()
    }
}
