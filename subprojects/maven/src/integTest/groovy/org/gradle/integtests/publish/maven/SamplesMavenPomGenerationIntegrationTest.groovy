/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Resources
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Rule
import spock.lang.Unroll

class SamplesMavenPomGenerationIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    public Resources resources = new Resources();

    @Rule
    public final Sample sample = new Sample(testDirectoryProvider, 'maven/pomGeneration')

    def setup() {
        executer.requireGradleDistribution()
        using m2 //uploadArchives leaks into local ~/.m2
    }

    @Unroll
    def "can deploy to local repository with #dsl dsl"() {
        given:
        def pomProjectDir = sample.dir.file(dsl)

        and:
        def repo = maven(pomProjectDir.file('pomRepo'))
        def module = repo.module('deployGroup', 'mywar', '1.0MVN')

        when:
        executer.inDirectory(pomProjectDir).withTasks('uploadArchives').run()

        then:
        compareXmlWithIgnoringOrder(expectedPom('1.0MVN', "deployGroup"), module.pomFile.text)
        module.moduleDir.file("mywar-1.0MVN.war").assertIsCopyOf(pomProjectDir.file("target/libs/mywar-1.0.war"))

        and:
        pomProjectDir.file('build').assertDoesNotExist()
        pomProjectDir.file('target').assertIsDir()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    def "can install to local repository with #dsl dsl"() {
        given:
        def pomProjectDir = sample.dir.file(dsl)

        and:
        def repo = m2.mavenRepo()
        def module = repo.module('installGroup', 'mywar', '1.0MVN')
        module.moduleDir.deleteDir()

        when:
        executer.inDirectory(pomProjectDir).withTasks('install').run()

        then:
        pomProjectDir.file('build').assertDoesNotExist()
        pomProjectDir.file('target').assertIsDir()

        and:
        TestFile installedFile = module.moduleDir.file("mywar-1.0MVN.war")
        TestFile installedJavadocFile = module.moduleDir.file("mywar-1.0MVN-javadoc.zip")
        TestFile installedPom = module.moduleDir.file("mywar-1.0MVN.pom")

        and:
        installedFile.assertIsCopyOf(pomProjectDir.file("target/libs/mywar-1.0.war"))
        installedJavadocFile.assertIsCopyOf(pomProjectDir.file("target/distributions/mywar-1.0-javadoc.zip"))
        installedPom.assertIsFile()

        and:
        compareXmlWithIgnoringOrder(expectedPom("1.0MVN", "installGroup"), installedPom.text)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    def "write new pom with #dsl dsl"() {
        given:
        def pomProjectDir = sample.dir.file(dsl)

        when:
        executer.inDirectory(pomProjectDir).withTasks('writeNewPom').run()

        then:
        compareXmlWithIgnoringOrder(expectedPom(null, null, 'pomGeneration/expectedNewPom.txt'), pomProjectDir.file("target/newpom.xml").text)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    def "write deployer pom with #dsl dsl"() {
        given:
        def pomProjectDir = sample.dir.file(dsl)

        and:
        String version = '1.0MVN'
        String groupId = "deployGroup"

        when:
        executer.inDirectory(pomProjectDir).withTasks('writeDeployerPom').run()

        then:
        compareXmlWithIgnoringOrder(expectedPom(version, groupId), pomProjectDir.file("target/deployerpom.xml").text)

        where:
        dsl << ['groovy', 'kotlin']
    }

    private String expectedPom(String version, String groupId, String path = 'pomGeneration/expectedPom.txt') {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        String text = resources.getResource(path).text
        return templateEngine.createTemplate(text).make(version: version, groupId: groupId)
    }

    private static void compareXmlWithIgnoringOrder(String expectedXml, String actualXml) {
        Diff diff = new Diff(expectedXml, actualXml)
        diff.overrideElementQualifier(new RecursiveElementNameAndTextQualifier())
        XMLAssert.assertXMLEqual(diff, true);
        Assert.assertThat(actualXml, CoreMatchers.startsWith(String.format('<?xml version="1.0" encoding="UTF-8"?>')))
    }
}
