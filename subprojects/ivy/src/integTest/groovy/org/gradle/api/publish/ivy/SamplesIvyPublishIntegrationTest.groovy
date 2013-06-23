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
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.util.TextUtil
import org.junit.Rule

public class SamplesIvyPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final Sample quickstart = new Sample(temporaryFolder, "ivy-publish/quickstart")
    @Rule public final Sample javaProject = new Sample(temporaryFolder, "ivy-publish/java-multi-project")
    @Rule public final Sample customization = new Sample(temporaryFolder, "ivy-publish/descriptor-customization")
    @Rule public final Sample multiPublish = new Sample(temporaryFolder, "ivy-publish/multiple-publications")

    def "quickstart sample"() {
        given:
        sample quickstart

        and:
        def fileRepo = ivy(quickstart.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "quickstart", "1.0")

        when:
        succeeds "publish"

        then:
        module.assertPublishedAsJavaModule()
    }

    def "java-multi-project sample"() {
        given:
        sample javaProject

        and:
        def fileRepo = ivy(javaProject.dir.file("build/repo"))
        def project1module = fileRepo.module("org.gradle.sample", "project1", "1.0")
        def project2module = fileRepo.module("org.gradle.sample", "project2", "1.0")

        when:
        succeeds "publish"

        then:
        project1module.assertPublished()
        project1module.assertArtifactsPublished("project1-1.0.jar", "project1-1.0-source.jar", "ivy-1.0.xml")

        project1module.ivy.configurations.keySet() == ['default', 'runtime'] as Set
        project1module.ivy.description == "The first project"
        project1module.ivy.assertDependsOn("junit:junit:4.11@runtime", "org.gradle.sample:project2:1.0@runtime")

        and:
        project2module.assertPublished()
        project2module.assertArtifactsPublished("project2-1.0.jar", "project2-1.0-source.jar", "ivy-1.0.xml")

        project2module.ivy.configurations.keySet() == ['default', 'runtime'] as Set
        project2module.ivy.description == "The second project"
        project2module.ivy.assertDependsOn('commons-collections:commons-collections:3.1@runtime')

        def actualIvyXmlText = project1module.ivyFile.text.replaceFirst('publication="\\d+"', 'publication="«PUBLICATION-TIME-STAMP»"').trim()
        actualIvyXmlText == getExpectedIvyOutput(javaProject.dir.file("output-ivy.xml"))
    }

    def "descriptor-customization sample"() {
        given:
        sample customization

        and:
        def fileRepo = ivy(customization.dir.file("build/repo"))
        def module = fileRepo.module("org.gradle.sample", "descriptor-customization", "1.0")

        when:
        succeeds "publish"

        then:
        module.assertPublished()
        module.ivy.description == "A demonstration of ivy descriptor customization"
    }

    def "multiple-publications sample"() {
        given:
        sample multiPublish

        and:
        def fileRepo = ivy(multiPublish.dir.file("build/repo"))
        def project1sample = fileRepo.module("org.gradle.sample", "project1-sample", "1.0")
        def project2api = fileRepo.module("org.gradle.sample", "project2-api", "2")
        def project2impl = fileRepo.module("org.gradle.sample.impl", "project2-impl", "2.3")

        when:
        succeeds "publish"

        then:
        project1sample.assertPublishedAsJavaModule()
        project1sample.ivy.assertDependsOn("junit:junit:4.11@runtime", "org.gradle.sample:project2-api:2@runtime", "org.gradle.sample.impl:project2-impl:2.3@runtime")

        verifyIvyFile(project1sample, "output/project1.ivy.xml")

        and:
        project2api.assertPublishedAsJavaModule()
        project2api.ivy.assertDependsOn()

        verifyIvyFile(project2api, "output/project2-api.ivy.xml")

        and:
        project2impl.assertPublishedAsJavaModule()
        project2impl.ivy.assertDependsOn('commons-collections:commons-collections:3.1@runtime')
        
        verifyIvyFile(project2impl, "output/project2-impl.ivy.xml")
    }

    private void verifyIvyFile(IvyFileModule project1sample, String outputFileName) {
        def actualIvyXmlText = project1sample.ivyFile.text.replaceFirst('publication="\\d+"', 'publication="«PUBLICATION-TIME-STAMP»"').trim()
        assert actualIvyXmlText == getExpectedIvyOutput(multiPublish.dir.file(outputFileName))
    }

    String getExpectedIvyOutput(File outputFile) {
        assert outputFile.file
        outputFile.readLines()[1..-1].join(TextUtil.getPlatformLineSeparator()).trim()
    }
}
