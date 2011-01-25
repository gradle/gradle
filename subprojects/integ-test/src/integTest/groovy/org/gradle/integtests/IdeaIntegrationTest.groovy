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

import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier
import org.custommonkey.xmlunit.XMLAssert
import org.gradle.integtests.fixtures.TestResources
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test
import junit.framework.AssertionFailedError

class IdeaIntegrationTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    void canCreateAndDeleteMetaData() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.ipr')
        assertHasExpectedContents('root.iws')
        assertHasExpectedContents('root.iml')
        assertHasExpectedContents('api/api.iml')
        assertHasExpectedContents('webservice/webservice.iml')

        executer.withTasks('cleanIdea').run()
    }

    @Test
    void worksWithAnEmptyProject() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.ipr')
        assertHasExpectedContents('root.iml')
    }

    @Test
    void worksWithASubProjectThatDoesNotHaveTheIdeaPluginApplied() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.ipr')
    }

    @Test
    void worksWithNonStandardLayout() {
        executer.inDirectory(testDir.file('root')).withTasks('idea').run()

        assertHasExpectedContents('root/root.ipr')
        assertHasExpectedContents('root/root.iml')
        assertHasExpectedContents('top-level.iml')
    }

    @Test
    void overwritesExistingDependencies() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.iml')
    }

    @Test
    void outputDirsDefaultToToIdeaDefaults() {
        def settingsFile = file("settings.gradle")
        settingsFile << "rootProject.name = 'root'"
        def buildFile = file("build.gradle")
        buildFile << "apply plugin: 'java'; apply plugin: 'idea'"

        executer.usingSettingsFile(settingsFile).usingBuildScript(buildFile).withTasks("idea").run()

        def module = parseImlFile("root")
        def outputUrl = module.component.output[0].@url
        def testOutputUrl = module.component."output-test"[0].@url

        assert outputUrl.text() == 'file://$MODULE_DIR$/out/production/root'
        assert testOutputUrl.text() == 'file://$MODULE_DIR$/out/test/root'
    }

    @Test
    void canHandleCircularModuleDependencies() {
        def repoDir = file("repo")
        def artifact1 = publishArtifact(repoDir, "myGroup", "myArtifact1", "myArtifact2")
        def artifact2 = publishArtifact(repoDir, "myGroup", "myArtifact2", "myArtifact1")

        def settingsFile = file("settings.gradle")
        settingsFile << "rootProject.name = 'root'"

        def buildFile = file("build.gradle")
        buildFile << """
apply plugin: "java"
apply plugin: "idea"

repositories {
    mavenRepo urls: "file://$repoDir.absolutePath"
}

dependencies {
    compile "myGroup:myArtifact1:1.0"
}
        """

        executer.usingSettingsFile(settingsFile).usingBuildScript(buildFile).withTasks("idea").run()

        def module = parseImlFile("root", true)
        def libs = module.component.orderEntry.library
        assert libs.size() == 2
        assert libs.CLASSES.root*.@url*.text().collect { new File(it).name } as Set == [artifact1.name + "!", artifact2.name + "!"] as Set
    }

    private void assertHasExpectedContents(String path) {
        TestFile file = testDir.file(path).assertIsFile()
        TestFile expectedFile = testDir.file("expectedFiles/${path}.xml").assertIsFile()

        def cache = distribution.userHomeDir.file("cache")
        def cachePath = cache.absolutePath.replace(File.separator, '/')
        def expectedXml = expectedFile.text.replace('@CACHE_DIR@', cachePath)

        Diff diff = new Diff(expectedXml, file.text)
        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier())
        try {
            XMLAssert.assertXMLEqual(diff, true)
        } catch (AssertionFailedError e) {
            throw new AssertionFailedError("generated file '$path' does not contain the expected contents: ${e.message}.\nExpected:\n${expectedXml}\nActual:\n${file.text}").initCause(e)
        }
    }

    private parseImlFile(projectName, print = false) {
        parseXmlFile("${projectName}.iml", print)
    }
}