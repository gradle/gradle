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
import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.internal.SystemProperties
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Resources
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SamplesMavenQuickstartIntegrationTest extends AbstractIntegrationTest {
    @Rule public Resources resources = new Resources();
    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'maven/quickstart')

    private TestFile pomProjectDir

    @Before
    void setUp() {
        pomProjectDir = sample.dir
    }

    @Test
    void "can publish to a local repository"() {
        executer.inDirectory(pomProjectDir).withTasks('uploadArchives').run()

        def repo = maven(pomProjectDir.file('pomRepo'))
        def module = repo.module('gradle', 'quickstart', '1.0')
        module.assertArtifactsPublished('quickstart-1.0.jar', 'quickstart-1.0.pom')
        compareXmlWithIgnoringOrder(expectedPom('1.0', "gradle"), module.pomFile.text)
        module.moduleDir.file("quickstart-1.0.jar").assertIsCopyOf(pomProjectDir.file('build/libs/quickstart-1.0.jar'))
    }

    @Test
    void "can install to local repository"() {
        def repo = maven(new TestFile("$SystemProperties.userHome/.m2/repository"))
        def module = repo.module('gradle', 'quickstart', '1.0')
        module.moduleDir.deleteDir()

        executer.inDirectory(pomProjectDir).withTasks('install').run()

        module.moduleDir.file("quickstart-1.0.jar").assertIsFile()
        module.moduleDir.file("quickstart-1.0.pom").assertIsFile()
        module.moduleDir.file("quickstart-1.0.jar").assertIsCopyOf(pomProjectDir.file('build/libs/quickstart-1.0.jar'))

        compareXmlWithIgnoringOrder(expectedPom('1.0', 'gradle'), module.pomFile.text)
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