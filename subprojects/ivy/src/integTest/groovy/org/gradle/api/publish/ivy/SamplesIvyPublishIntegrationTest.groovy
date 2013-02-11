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
import org.gradle.util.TextUtil
import org.junit.Rule

public class SamplesIvyPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final Sample quickstart = new Sample(temporaryFolder, "ivy-publish/quickstart")
    @Rule public final Sample javaProject = new Sample(temporaryFolder, "ivy-publish/java-multi-project")
    @Rule public final Sample customization = new Sample(temporaryFolder, "ivy-publish/descriptor-customization")

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

        with (project1module.ivy) {
            configurations.keySet() == ['default', 'runtime'] as Set

            dependencies.runtime.assertDependsOn('junit', 'junit', '4.11')
            dependencies.runtime.assertDependsOn('org.gradle.sample', 'project2', '1.0')

            description == "The first project"
        }

        and:
        project2module.assertPublished()
        project2module.assertArtifactsPublished("project2-1.0.jar", "project2-1.0-source.jar", "ivy-1.0.xml")

        with (project2module.ivy) {
            configurations.keySet() == ['default', 'runtime'] as Set

            dependencies.runtime.assertDependsOn('commons-collections', 'commons-collections', '3.1')

            description == "The second project"
        }

        def actualIvyXmlText = project1module.ivyFile.text.replaceFirst('publication="\\d+"', 'publication="«PUBLICATION-TIME-STAMP»"').trim()
        actualIvyXmlText == getExpectedIvyOutput(javaProject.dir.file("output-ivy.xml"))
    }

    String getExpectedIvyOutput(def outputFile) {
        outputFile.readLines()[1..-1].join(TextUtil.getPlatformLineSeparator()).trim()
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
}
