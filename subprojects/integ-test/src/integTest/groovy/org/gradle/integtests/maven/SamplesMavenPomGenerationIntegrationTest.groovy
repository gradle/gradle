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
package org.gradle.integtests.maven

import groovy.text.SimpleTemplateEngine
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.XMLAssert
import org.custommonkey.xmlunit.examples.RecursiveElementNameAndTextQualifier
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.util.Resources
import org.gradle.util.TestFile
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import static org.junit.Assert.*
import org.gradle.integtests.fixtures.Sample

/**
 * @author Hans Dockter
 */
class SamplesMavenPomGenerationIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

    private TestFile pomProjectDir
    private TestFile repoDir
    private TestFile snapshotRepoDir

    @Rule public Resources resources = new Resources();
    @Rule public final Sample sample = new Sample('maven/pomGeneration')

    @Before
    void setUp() {
        pomProjectDir = sample.dir
        repoDir = pomProjectDir.file('pomRepo');
        snapshotRepoDir = pomProjectDir.file('snapshotRepo');
    }
    
    @Test
    void checkWithNoCustomVersion() {
        String version = '1.0'
        String groupId = "gradle"
        long start = System.currentTimeMillis();
        executer.inDirectory(pomProjectDir).withTasks('clean', 'uploadArchives', 'install').run()
        String repoPath = repoPath(groupId, version)
        compareXmlWithIgnoringOrder(expectedPom(version, groupId),
                pomFile(repoDir, repoPath, version).text)
        repoDir.file("$repoPath/mywar-${version}.war").assertIsCopyOf(pomProjectDir.file("target/libs/mywar-${version}.war"))
        pomProjectDir.file('build').assertDoesNotExist()
        pomProjectDir.file('target').assertIsDir()
        checkInstall(start, pomProjectDir, version, groupId)
    }

    @Test
    void checkWithCustomVersion() {
        long start = System.currentTimeMillis();
        String version = "1.0MVN"
        String groupId = "deployGroup"
        executer.inDirectory(pomProjectDir).withArguments("-PcustomVersion=${version}").withTasks('clean', 'uploadArchives', 'install').run()
        String repoPath = repoPath(groupId, version)
        compareXmlWithIgnoringOrder(expectedPom(version, groupId),
                pomFile(repoDir, repoPath, version).text)
        repoDir.file("$repoPath/mywar-${version}.war").assertIsCopyOf(pomProjectDir.file("target/libs/mywar-1.0.war"))
        repoDir.file("$repoPath/mywar-${version}-javadoc.zip").assertIsCopyOf(pomProjectDir.file("target/distributions/mywar-1.0-javadoc.zip"))
        pomProjectDir.file('build').assertDoesNotExist()
        pomProjectDir.file('target').assertIsDir()
        checkInstall(start, pomProjectDir, version, 'installGroup')
    }

    @Test
    void checkWithSnapshotVersion() {
        String version = '1.0-SNAPSHOT'
        String groupId = "deployGroup"
        long start = System.currentTimeMillis();
        executer.inDirectory(pomProjectDir).withArguments("-PcustomVersion=${version}").withTasks('clean', 'uploadArchives', 'install').run()
        String repoPath = repoPath(groupId, version)
        File pomFile = pomFile(snapshotRepoDir, repoPath, version)
        compareXmlWithIgnoringOrder(expectedPom(version, groupId), pomFile.text)
        new TestFile(new File(pomFile.absolutePath.replace(".pom", ".war"))).assertIsFile()
        pomProjectDir.file('build').assertDoesNotExist()
        pomProjectDir.file('target').assertIsDir()
        checkInstall(start, pomProjectDir, version, 'installGroup')
    }

    @Test
    void writeNewPom() {
        executer.inDirectory(pomProjectDir).withTasks('clean', 'writeNewPom').run()
        compareXmlWithIgnoringOrder(expectedPom(null, null, 'pomGeneration/expectedNewPom.txt'),
                pomProjectDir.file("target/newpom.xml").text)
    }

    @Test
    void writeDeployerPom() {
        String version = '1.0'
        String groupId = "gradle"
        executer.inDirectory(pomProjectDir).withTasks('clean', 'writeDeployerPom').run()
        compareXmlWithIgnoringOrder(expectedPom(version, groupId), pomProjectDir.file("target/deployerpom.xml").text)
    }
    
    static String repoPath(String group, String version) {
        "$group/mywar/$version"
    }

    static File pomFile(TestFile repoDir, String repoPath, String version) {
        TestFile versionDir = repoDir.file(repoPath)
        List matches = versionDir.listFiles().findAll { it.name.endsWith('.pom') }
        assert matches.size() == 1
        matches[0]
    }

    void checkInstall(long start, TestFile pomProjectDir, String version, String groupId) {
        TestFile localMavenRepo = new TestFile(pomProjectDir.file("target/localRepoPath.txt").text as File)
        TestFile installedFile = localMavenRepo.file("$groupId/mywar/$version/mywar-${version}.war")
        TestFile installedJavadocFile = localMavenRepo.file("$groupId/mywar/$version/mywar-${version}-javadoc.zip")
        TestFile installedPom = localMavenRepo.file("$groupId/mywar/$version/mywar-${version}.pom")
        installedFile.assertIsCopyOf(pomProjectDir.file("target/libs/mywar-1.0.war"))
        installedJavadocFile.assertIsCopyOf(pomProjectDir.file("target/distributions/mywar-1.0-javadoc.zip"))
        installedPom.assertIsFile()
        assert start.intdiv(2000) <= installedFile.lastModified().intdiv(2000)
        assert start.intdiv(2000) <= installedJavadocFile.lastModified().intdiv(2000)
        compareXmlWithIgnoringOrder(expectedPom(version, groupId), installedPom.text)
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
        Assert.assertThat(actualXml, Matchers.startsWith(String.format('<?xml version="1.0" encoding="UTF-8"?>')))
    }
}