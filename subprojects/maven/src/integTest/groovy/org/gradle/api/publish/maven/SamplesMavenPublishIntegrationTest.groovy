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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.maven.M2Installation
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.util.TextUtil
import org.junit.Rule

public class SamplesMavenPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final Sample quickstart = new Sample(temporaryFolder, "maven-publish/quickstart")
    @Rule public final Sample javaProject = new Sample(temporaryFolder, "maven-publish/javaProject")
    @Rule public final Sample pomCustomization = new Sample(temporaryFolder, "maven-publish/pomCustomization")
    @Rule public final Sample multiPublish = new Sample(temporaryFolder, "maven-publish/multiple-publications")

    def quickstartPublish() {
        given:
        sample quickstart

        and:
        def fileRepo = maven(quickstart.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "quickstart", "1.0")

        when:
        succeeds "publish"

        then:
        def pom = module.parsedPom
        module.assertPublishedAsJavaModule()
        pom.scopes.isEmpty()
    }

    def quickstartPublishLocal() {
        given:
        def m2Installation = new M2Installation(testDirectory)
        executer.beforeExecute m2Installation
        def localModule = m2Installation.mavenRepo().module("org.gradle.sample", "quickstart", "1.0")

        and:
        sample quickstart

        and:
        def fileRepo = maven(quickstart.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "quickstart", "1.0")

        when:
        succeeds 'publishToMavenLocal'

        then: "jar is published to maven local repository"
        module.assertNotPublished()
        localModule.assertPublishedAsJavaModule()
    }

    def javaProject() {
        given:
        sample javaProject

        and:
        def fileRepo = maven(javaProject.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "javaProject", "1.0")

        when:
        succeeds "publish"

        then:
        module.assertPublished()
        module.assertArtifactsPublished("javaProject-1.0.jar", "javaProject-1.0-sources.jar", "javaProject-1.0.pom")
        module.parsedPom.packaging == null
        module.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:3.0")
    }

    def pomCustomization() {
        given:
        sample pomCustomization

        and:
        def fileRepo = maven(pomCustomization.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "pomCustomization", "1.0")

        when:
        succeeds "publish"

        then:
        module.assertPublishedAsPomModule()
        module.parsedPom.description == "A demonstration of maven pom customisation"
    }

    def multiplePublications() {
        given:
        sample multiPublish

        and:
        def fileRepo = maven(multiPublish.dir.file("build/repo"))
        def project1sample = fileRepo.module("org.gradle.sample", "project1-sample", "1.0")
        def project2api = fileRepo.module("org.gradle.sample", "project2-api", "2")
        def project2impl = fileRepo.module("org.gradle.sample.impl", "project2-impl", "2.3")

        when:
        succeeds "publish"

        then:
        project1sample.assertPublishedAsJavaModule()
        project1sample.parsedPom.scopes.runtime
                .assertDependsOn("org.slf4j:slf4j-api:1.7.5", "org.gradle.sample:project2-api:2", "org.gradle.sample.impl:project2-impl:2.3")

        verifyPomFile(project1sample, "output/project1.pom.xml")

        and:
        project2api.assertPublishedAsJavaModule()
        project2api.parsedPom.scopes.runtime == null

        verifyPomFile(project2api, "output/project2-api.pom.xml")

        and:
        project2impl.assertPublishedAsJavaModule()
        project2impl.parsedPom.scopes.runtime.assertDependsOn('commons-collections:commons-collections:3.1')

        verifyPomFile(project2impl, "output/project2-impl.pom.xml")
    }

    private void verifyPomFile(MavenFileModule module, String outputFileName) {
        def actualIvyXmlText = module.pomFile.text.replaceFirst('publication="\\d+"', 'publication="«PUBLICATION-TIME-STAMP»"').trim()
        assert actualIvyXmlText == getExpectedIvyOutput(multiPublish.dir.file(outputFileName))
    }

    String getExpectedIvyOutput(File outputFile) {
        assert outputFile.file
        outputFile.readLines()[1..-1].join(TextUtil.getPlatformLineSeparator()).trim()
    }
}
