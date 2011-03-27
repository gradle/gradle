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

package org.gradle.plugins.ide.idea

import junit.framework.AssertionFailedError
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier
import org.custommonkey.xmlunit.XMLAssert
import org.gradle.integtests.fixtures.TestResources
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test
import org.gradle.plugins.ide.AbstractIdeIntegrationTest

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
        runIdeaTask("apply plugin: 'java'; apply plugin: 'idea'")

        def module = parseImlFile("root")
        assert module.component.@"inherit-compiler-output" == "true"
    }

    @Test
    void canHandleCircularModuleDependencies() {
        def repoDir = file("repo")
        def artifact1 = publishArtifact(repoDir, "myGroup", "myArtifact1", "myArtifact2")
        def artifact2 = publishArtifact(repoDir, "myGroup", "myArtifact2", "myArtifact1")

        runIdeaTask """
apply plugin: "java"
apply plugin: "idea"

repositories {
    mavenRepo urls: "${repoDir.toURI()}"
}

dependencies {
    compile "myGroup:myArtifact1:1.0"
}
        """

        def module = parseImlFile("root")
        def libs = module.component.orderEntry.library
        assert libs.size() == 2
        assert libs.CLASSES.root*.@url*.text().collect { new File(it).name } as Set == [artifact1.name + "!", artifact2.name + "!"] as Set
    }

    @Test
    void onlyAddsSourceDirsThatExistOnFileSystem() {
        runIdeaTask """
apply plugin: "java"
apply plugin: "groovy"
apply plugin: "idea"

sourceSets.main.java.srcDirs.each { it.mkdirs() }
sourceSets.main.resources.srcDirs.each { it.mkdirs() }
sourceSets.test.groovy.srcDirs.each { it.mkdirs() }
        """

        def module = parseImlFile("root")
        def sourceFolders = module.component.content.sourceFolder
        def urls = sourceFolders*.@url*.text()

        assert containsDir("src/main/java", urls)
        assert !containsDir("src/main/groovy", urls)
        assert containsDir("src/main/resources", urls)
        assert !containsDir("src/test/java", urls)
        assert containsDir("src/test/groovy", urls)
        assert !containsDir("src/test/resources", urls)
    }

    @Test
    void triggersBeforeAndWhenConfigurationHooks() {
        //this test is a bit peculiar as it has assertions inside the gradle script
        //couldn't find a better way of asserting on before/when configured hooks
        runIdeaTask '''
apply plugin: 'java'
apply plugin: 'idea'

def beforeConfiguredObjects = 0
def whenConfiguredObjects = 0

ideaModule {
    beforeConfigured { beforeConfiguredObjects++ }
    whenConfigured { whenConfiguredObjects++ }
}
ideaProject {
    beforeConfigured { beforeConfiguredObjects++ }
    whenConfigured { whenConfiguredObjects++ }
}
ideaWorkspace {
    beforeConfigured { beforeConfiguredObjects++ }
    whenConfigured { whenConfiguredObjects++ }
}

idea << {
    assert beforeConfiguredObjects == 3 : "beforeConfigured() hooks shoold be fired for domain model objects"
    assert whenConfiguredObjects == 3 : "whenConfigured() hooks shoold be fired for domain model objects"
}
'''

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

    private runIdeaTask(buildScript) {
        runTask("idea", buildScript)
    }

    private parseImlFile(Map options = [:], String projectName) {
        parseFile(options, "${projectName}.iml")
    }

    private containsDir(path, urls) {
        urls.any { it.endsWith(path) }
    }
}
