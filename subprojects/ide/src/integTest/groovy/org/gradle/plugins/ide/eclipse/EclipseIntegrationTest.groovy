/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test

class EclipseIntegrationTest extends AbstractEclipseIntegrationTest {
    private static String nonAscii = "\\u7777\\u8888\\u9999"

    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    void canCreateAndDeleteMetaData() {
        File buildFile = testFile("master/build.gradle")
        usingBuildFile(buildFile).run()
    }

    @Test
    void sourceEntriesInClasspathFileAreSortedAsPerUsualConvention() {
        def expectedOrder = [
                "src/main/java",
                "src/main/groovy",
                "src/main/resources",
                "src/test/java",
                "src/test/groovy",
                "src/test/resources",
                "src/integTest/java",
                "src/integTest/groovy",
                "src/integTest/resources"
        ]

        expectedOrder.each { testFile(it).mkdirs() }

        runEclipseTask """
apply plugin: "java"
apply plugin: "groovy"
apply plugin: "eclipse"

sourceSets {
    integTest {
        resources { srcDir "src/integTest/resources" }
        java { srcDir "src/integTest/java" }
        groovy { srcDir "src/integTest/groovy" }
    }
}
        """

        def classpath = parseClasspathFile()
        def sourceEntries = findEntries(classpath, "src")
        assert sourceEntries*.@path == expectedOrder
    }

    @Test
    void outputDirDefaultsToEclipseDefault() {
        runEclipseTask("apply plugin: 'java'; apply plugin: 'eclipse'")

        def classpath = parseClasspathFile()

        def outputs = findEntries(classpath, "output")
        assert outputs*.@path == ["bin"]

        def sources = findEntries(classpath, "src")
        sources.each { assert !it.attributes().containsKey("path") }
    }

    @Test
    void canHandleCircularModuleDependencies() {
        def repoDir = file("repo")
        def artifact1 = publishArtifact(repoDir, "myGroup", "myArtifact1", "myArtifact2")
        def artifact2 = publishArtifact(repoDir, "myGroup", "myArtifact2", "myArtifact1")

        runEclipseTask """
apply plugin: "java"
apply plugin: "eclipse"

repositories {
    mavenRepo urls: "${repoDir.toURI()}"
}

dependencies {
    compile "myGroup:myArtifact1:1.0"
}
        """

        libEntriesInClasspathFileHaveFilenames(artifact1.name, artifact2.name)
    }

    @Test
    void eclipseFilesAreWrittenWithUtf8Encoding() {
        runEclipseTask """
apply plugin: "war"
apply plugin: "eclipse"

eclipseProject {
  projectName = "$nonAscii"
}

eclipseClasspath {
  containers("$nonAscii")
}

eclipseWtpComponent {
  deployName = "$nonAscii"
}

eclipseWtpFacet {
  facet([name: "$nonAscii"])
}
        """

        checkIsWrittenWithUtf8Encoding(getProjectFile())
        checkIsWrittenWithUtf8Encoding(getClasspathFile())
        checkIsWrittenWithUtf8Encoding(getComponentFile())
        checkIsWrittenWithUtf8Encoding(getFacetFile())
    }

    @Test
    void triggersBeforeAndWhenConfigurationHooks() {
        //this test is a bit peculiar as it has assertions inside the gradle script
        //couldn't find a better way of asserting on before/when configured hooks
        runEclipseTask('''
apply plugin: 'java'
apply plugin: 'war'
apply plugin: 'eclipse'

def beforeConfiguredObjects = 0
def whenConfiguredObjects = 0

eclipseProject {
    beforeConfigured { beforeConfiguredObjects++ }
    whenConfigured { whenConfiguredObjects++ }
}
eclipseClasspath {
    beforeConfigured { beforeConfiguredObjects++ }
    whenConfigured { whenConfiguredObjects++ }
}
eclipseWtpFacet {
    beforeConfigured { beforeConfiguredObjects++ }
    whenConfigured { whenConfiguredObjects++ }
}
eclipseWtpComponent {
    beforeConfigured { beforeConfiguredObjects++ }
    whenConfigured { whenConfiguredObjects++ }
}
eclipseJdt {
    beforeConfigured { beforeConfiguredObjects++ }
    whenConfigured { whenConfiguredObjects++ }
}

eclipse << {
    assert beforeConfiguredObjects == 5 : "beforeConfigured() hooks shoold be fired for domain model objects"
    assert whenConfiguredObjects == 5 : "whenConfigured() hooks shoold be fired for domain model objects"
}
''')

    }

    private void checkIsWrittenWithUtf8Encoding(File file) {
        def text = file.getText("UTF-8")
        assert text.contains('encoding="UTF-8"')
        String expectedNonAsciiChars = "\u7777\u8888\u9999"
        assert text.contains(expectedNonAsciiChars)
    }
}
