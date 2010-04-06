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
package org.gradle.integtests

import groovy.text.SimpleTemplateEngine
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.XMLAssert
import org.custommonkey.xmlunit.examples.RecursiveElementNameAndTextQualifier
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.runner.RunWith
import org.junit.Test
import org.junit.Before
import static org.junit.Assert.*
import org.junit.Rule
import org.gradle.util.Resources
import org.gradle.util.TestFile

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class SamplesMavenPomGenerationIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    private final GradleExecuter executer = dist.executer

    private TestFile pomProjectDir
    private TestFile repoDir
    private TestFile snapshotRepoDir

    @Rule public Resources resources = new Resources();

    @Before
    public void setUp() {
        pomProjectDir = dist.samplesDir.file('maven/pomGeneration')
        repoDir = pomProjectDir.file('pomRepo');
        snapshotRepoDir = pomProjectDir.file('snapshotRepo');
    }
    
    @Test
    public void checkWithNoCustomVersion() {
        String version = '1.0'
        String groupId = "gradle"
        long start = System.currentTimeMillis();
        executer.inDirectory(pomProjectDir).withTasks('clean', 'uploadArchives', 'install').run()
        String repoPath = repoPath(groupId, version)
        compareXmlWithIgnoringOrder(expectedPom(version, groupId),
                pomFile(repoDir, repoPath, version).text)
        repoDir.file("$repoPath/mywar-${version}.war").assertIsFile()
        pomProjectDir.file('build').assertDoesNotExist()
        pomProjectDir.file('target').assertIsDir()
        checkInstall(start, pomProjectDir, version, groupId)
    }

    @Test
    public void checkWithCustomVersion() {
        long start = System.currentTimeMillis();
        String version = "1.0MVN"
        String groupId = "deployGroup"
        executer.inDirectory(pomProjectDir).withArguments("-PcustomVersion=${version}").withTasks('clean', 'uploadArchives', 'install').run()
        String repoPath = repoPath(groupId, version)
        compareXmlWithIgnoringOrder(expectedPom(version, groupId),
                pomFile(repoDir, repoPath, version).text)
        repoDir.file("$repoPath/mywar-${version}.war").assertIsFile()
        repoDir.file("$repoPath/mywar-${version}-javadoc.zip").assertIsFile()
        pomProjectDir.file('build').assertDoesNotExist()
        pomProjectDir.file('target').assertIsDir()
        checkInstall(start, pomProjectDir, version, 'installGroup')
    }

    @Test
    public void checkWithSnapshotVersion() {
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
    public void writeNewPom() {
        executer.inDirectory(pomProjectDir).withTasks('clean', 'writeNewPom').run()
        compareXmlWithIgnoringOrder(expectedPom(null, null, 'pomGeneration/expectedNewPom.txt'),
                pomProjectDir.file("target/newpom.xml").text)
    }

    @Test
    public void writeDeployerPom() {
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
        assertEquals(1, matches.size())
        matches[0]
    }

    void checkInstall(long start, TestFile pomProjectDir, String version, String groupId) {
        TestFile localMavenRepo = new TestFile(pomProjectDir.file("target/localRepoPath.txt").text as File)
        TestFile installedFile = localMavenRepo.file("$groupId/mywar/$version/mywar-${version}.war")
        TestFile installedJavadocFile = localMavenRepo.file("$groupId/mywar/$version/mywar-${version}-javadoc.zip")
        TestFile installedPom = localMavenRepo.file("$groupId/mywar/$version/mywar-${version}.pom")
        installedFile.assertIsFile()
        installedJavadocFile.assertIsFile()
        installedPom.assertIsFile()
        Assert.assertTrue(start <= installedFile.lastModified());
        Assert.assertTrue(start <= installedJavadocFile.lastModified());
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