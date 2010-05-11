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
package org.gradle.integtests

import groovy.text.SimpleTemplateEngine
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.XMLAssert
import org.custommonkey.xmlunit.examples.RecursiveElementNameAndTextQualifier
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.util.Resources
import org.gradle.util.TestFile
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class SamplesMavenQuickstartIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

    private TestFile pomProjectDir
    private TestFile repoDir

    @Rule public Resources resources = new Resources();

    @Before
    public void setUp() {
        pomProjectDir = dist.samplesDir.file('maven/quickstart')
        repoDir = pomProjectDir.file('pomRepo');
    }

    @Test
    public void checkDeployAndInstall() {
        String version = '1.0'
        String groupId = "gradle"
        long start = System.currentTimeMillis();
        executer.inDirectory(pomProjectDir).withTasks('clean', 'uploadArchives', 'install').run()
        String repoPath = repoPath(groupId, version)
        compareXmlWithIgnoringOrder(expectedPom(version, groupId),
                pomFile(repoDir, repoPath, version).text)
        repoDir.file("$repoPath/quickstart-${version}.jar").assertIsFile()
        checkInstall(start, pomProjectDir, version, groupId)
    }

    static String repoPath(String group, String version) {
        "$group/quickstart/$version"
    }

    static File pomFile(TestFile repoDir, String repoPath, String version) {
        TestFile versionDir = repoDir.file(repoPath)
        List matches = versionDir.listFiles().findAll { it.name.endsWith('.pom') }
        assertEquals(1, matches.size())
        matches[0]
    }

    void checkInstall(long start, TestFile pomProjectDir, String version, String groupId) {
        TestFile localMavenRepo = new TestFile(pomProjectDir.file("build/localRepoPath.txt").text as File)
        TestFile installedFile = localMavenRepo.file("$groupId/quickstart/$version/quickstart-${version}.jar")
        TestFile installedPom = localMavenRepo.file("$groupId/quickstart/$version/quickstart-${version}.pom")
        installedFile.assertIsFile()
        installedPom.assertIsFile()
        Assert.assertTrue(start <= installedFile.lastModified());
        compareXmlWithIgnoringOrder(expectedPom(version, groupId), installedPom.text)
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