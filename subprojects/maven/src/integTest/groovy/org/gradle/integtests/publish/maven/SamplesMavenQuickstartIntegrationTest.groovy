/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests.publish.maven

import groovy.text.SimpleTemplateEngine
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.XMLAssert
import org.custommonkey.xmlunit.examples.RecursiveElementNameAndTextQualifier
import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.Sample
import org.gradle.util.Resources
import org.junit.Rule
import spock.lang.Unroll

class SamplesMavenQuickstartIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    public Resources resources = new Resources();

    @Rule
    public final Sample sample = new Sample(testDirectoryProvider, 'maven/quickstart')

    def setup() {
        executer.requireGradleDistribution()
        using m2
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "can publish to a local repository with #dsl dsl"() {
        given:
        def pomProjectDir = sample.dir.file(dsl)

        when:
        executer.expectDeprecationWarnings(2)
        executer.inDirectory(pomProjectDir).withTasks('uploadArchives').run()

        then:
        def repo = maven(pomProjectDir.file('pomRepo'))
        def module = repo.module('gradle', 'quickstart', '1.0').withoutExtraChecksums()
        module.assertArtifactsPublished('quickstart-1.0.jar', 'quickstart-1.0.pom')
        compareXmlWithIgnoringOrder(expectedPom('1.0', "gradle"), module.pomFile.text)
        module.moduleDir.file("quickstart-1.0.jar").assertIsCopyOf(pomProjectDir.file('build/libs/quickstart-1.0.jar'))

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "can install to local repository with #dsl dsl"() {
        given:
        def pomProjectDir = sample.dir.file(dsl)

        and:
        def module = m2.mavenRepo().module('gradle', 'quickstart', '1.0')
        module.moduleDir.deleteDir()

        when:
        executer.expectDeprecationWarning()
        executer.inDirectory(pomProjectDir).withTasks('install').run()

        then:
        module.moduleDir.file("quickstart-1.0.jar").assertIsFile()
        module.moduleDir.file("quickstart-1.0.pom").assertIsFile()
        module.moduleDir.file("quickstart-1.0.jar").assertIsCopyOf(pomProjectDir.file('build/libs/quickstart-1.0.jar'))

        and:
        compareXmlWithIgnoringOrder(expectedPom('1.0', 'gradle'), module.pomFile.text)

        where:
        dsl << ['groovy', 'kotlin']
    }

    private String expectedPom(String version, String groupId) {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        String text = resources.getResource('pomGeneration/expectedQuickstartPom.txt').text
        return templateEngine.createTemplate(text).make(version: version, groupId: groupId)
    }

    private static void compareXmlWithIgnoringOrder(String expectedXml, String actualXml) {
        Diff diff = new Diff(expectedXml, actualXml)
        diff.overrideElementQualifier(new RecursiveElementNameAndTextQualifier())
        XMLAssert.assertXMLEqual(diff, true);
    }
}
