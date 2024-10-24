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

import junit.framework.AssertionFailedError
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier
import org.custommonkey.xmlunit.XMLAssert
import org.gradle.api.JavaVersion
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil
import org.junit.ComparisonFailure
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

import java.util.regex.Pattern

class EclipseIntegrationTest extends AbstractEclipseIntegrationTest {
    private static String nonAscii = "\\u7777\\u8888\\u9999"

    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Test
    @ToBeFixedForConfigurationCache
    void canCreateAndDeleteMetaData() {
        when:
        executer.withTasks("eclipse").run()

        assertHasExpectedContents(getClasspathFile(project:"api"), "apiClasspath.xml")
        assertHasExpectedContents(getProjectFile(project:"api"), "apiProject.xml")
        assertHasExpectedContents(getComponentFile(project:"api"), "apiWtpComponent.xml")
        assertHasExpectedContents(getFacetFile(project:"api"), "apiWtpFacet.xml")
        assertHasExpectedProperties(getJdtPropertiesFile(project:"api"), "apiJdt.properties")

        assertHasExpectedContents(getClasspathFile(project:"common"), "commonClasspath.xml")
        assertHasExpectedContents(getProjectFile(project:"common"), "commonProject.xml")
        assertHasExpectedContents(getComponentFile(project:"common"), "commonWtpComponent.xml")
        assertHasExpectedContents(getFacetFile(project:"common"), "commonWtpFacet.xml")
        assertHasExpectedProperties(getJdtPropertiesFile(project:"common"), "commonJdt.properties")

        assertHasExpectedContents(getClasspathFile(project:"groovyproject"), "groovyprojectClasspath.xml")
        assertHasExpectedContents(getProjectFile(project:"groovyproject"), "groovyprojectProject.xml")
        assertHasExpectedProperties(getJdtPropertiesFile(project:"groovyproject"), "groovyprojectJdt.properties")

        assertHasExpectedContents(getClasspathFile(project:"javabaseproject"), "javabaseprojectClasspath.xml")
        assertHasExpectedContents(getProjectFile(project:"javabaseproject"), "javabaseprojectProject.xml")
        assertHasExpectedProperties(getJdtPropertiesFile(project:"javabaseproject"), "javabaseprojectJdt.properties")

        assertHasExpectedContents(getProjectFile(), "masterProject.xml")

        assertHasExpectedContents(getClasspathFile(project:"webAppJava6"), "webAppJava6Classpath.xml")
        assertHasExpectedContents(getProjectFile(project:"webAppJava6"), "webAppJava6Project.xml")
        assertHasExpectedContents(getComponentFile(project:"webAppJava6"), "webAppJava6WtpComponent.xml")
        assertHasExpectedContents(getFacetFile(project:"webAppJava6"), "webAppJava6WtpFacet.xml")
        assertHasExpectedProperties(getJdtPropertiesFile(project:"webAppJava6"), "webAppJava6Jdt.properties")

        assertHasExpectedContents(getClasspathFile(project:"webAppWithVars"), "webAppWithVarsClasspath.xml")
        assertHasExpectedContents(getProjectFile(project:"webAppWithVars"), "webAppWithVarsProject.xml")
        assertHasExpectedContents(getComponentFile(project:"webAppWithVars"), "webAppWithVarsWtpComponent.xml")
        assertHasExpectedContents(getFacetFile(project:"webAppWithVars"), "webAppWithVarsWtpFacet.xml")
        assertHasExpectedProperties(getJdtPropertiesFile(project:"webAppWithVars"), "webAppWithVarsJdt.properties")

        assertHasExpectedContents(getClasspathFile(project:"webservice"), "webserviceClasspath.xml")
        assertHasExpectedContents(getProjectFile(project:"webservice"), "webserviceProject.xml")
        assertHasExpectedContents(getComponentFile(project:"webservice"), "webserviceWtpComponent.xml")
        assertHasExpectedContents(getFacetFile(project:"webservice"), "webserviceWtpFacet.xml")
        assertHasExpectedProperties(getJdtPropertiesFile(project:"webservice"), "webserviceJdt.properties")

        executer.withTasks("cleanEclipse").run()
    }

    @Test
    @ToBeFixedForConfigurationCache
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
    @ToBeFixedForConfigurationCache
    void outputDirDefaultsToEclipseDefault() {
        runEclipseTask("apply plugin: 'java'; apply plugin: 'eclipse'")

        def classpath = parseClasspathFile()

        def outputs = findEntries(classpath, "output")
        assert outputs*.@path == [EclipsePluginConstants.DEFAULT_PROJECT_OUTPUT_PATH]

        def sources = findEntries(classpath, "src")
        sources.each { assert !it.attributes().containsKey("path") }
    }

    @Test
    @ToBeFixedForConfigurationCache
    void canHandleCircularModuleDependencies() {
        def artifact1 = mavenRepo.module("myGroup", "myArtifact1", "1.0").dependsOn("myGroup", "myArtifact2", "1.0").publish().artifactFile
        def artifact2 = mavenRepo.module("myGroup", "myArtifact2", "1.0").dependsOn("myGroup", "myArtifact1", "1.0").publish().artifactFile

        runEclipseTask """
apply plugin: "java"
apply plugin: "eclipse"

repositories {
    maven { url = "${mavenRepo.uri}" }
}

dependencies {
    implementation "myGroup:myArtifact1:1.0"
}
        """

        libEntriesInClasspathFileHaveFilenames(artifact1.name, artifact2.name)
    }

    @Test
    @ToBeFixedForConfigurationCache
    void canConfigureTargetRuntimeName() {

        runEclipseTask """
apply plugin: "java"
apply plugin: "eclipse"

repositories {
    maven { url = "${mavenRepo.uri}" }
}

eclipse {
    jdt {
        javaRuntimeName = "Jigsaw-1.9"
    }
}"""

        assert classpath.containers == ["org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/Jigsaw-1.9/"]
    }

    @Test
    @ToBeFixedForConfigurationCache
    void eclipseFilesAreWrittenWithUtf8Encoding() {
        runEclipseTask """
apply plugin: "war"
apply plugin: "eclipse-wtp"

eclipse {
    project.name = "$nonAscii"
    classpath {
        containers "$nonAscii"
    }

    wtp {
        component {
            deployName = "$nonAscii"
        }
        facet {
            facet name: "$nonAscii"
        }
    }
}
        """

        checkIsWrittenWithUtf8Encoding(getProjectFile())
        checkIsWrittenWithUtf8Encoding(getClasspathFile())
        checkIsWrittenWithUtf8Encoding(getComponentFile())
        checkIsWrittenWithUtf8Encoding(getFacetFile())
    }

    @Test
    @ToBeFixedForConfigurationCache
    void triggersBeforeAndWhenConfigurationHooks() {
        //this test is a bit peculiar as it has assertions inside the gradle script
        //couldn't find a better way of asserting on before/when configured hooks
        runEclipseTask('''
apply plugin: 'java'
apply plugin: 'war'
apply plugin: 'eclipse-wtp'

def beforeConfiguredObjects = 0
def whenConfiguredObjects = 0

eclipse {
    project {
        file {
            beforeMerged {beforeConfiguredObjects++ }
            whenMerged {whenConfiguredObjects++ }
        }
    }

    classpath {
        file {
            beforeMerged {beforeConfiguredObjects++ }
            whenMerged {whenConfiguredObjects++ }
        }
    }

    wtp.component {
        file {
            beforeMerged {beforeConfiguredObjects++ }
            whenMerged {whenConfiguredObjects++ }
        }
    }

    wtp.facet {
        file {
            beforeMerged {beforeConfiguredObjects++ }
            whenMerged {whenConfiguredObjects++ }
        }
    }

    jdt {
        file {
            beforeMerged {beforeConfiguredObjects++ }
            whenMerged {whenConfiguredObjects++ }
        }
    }
}

tasks.eclipse {
    doLast {
        assert beforeConfiguredObjects == 5 : "beforeConfigured() hooks should be fired for domain model objects"
        assert whenConfiguredObjects == 5 : "whenConfigured() hooks should be fired for domain model objects"
    }
}
''')

    }

    @Test
    @ToBeFixedForConfigurationCache
    void respectsPerConfigurationExcludes() {
        def artifact1 = mavenRepo.module("myGroup", "myArtifact1", "1.0").dependsOn("myGroup", "myArtifact2", "1.0").publish().artifactFile
        mavenRepo.module("myGroup", "myArtifact2", "1.0").publish()

        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url = "${mavenRepo.uri}" }
}

configurations {
    implementation.exclude module: 'myArtifact2'
}

dependencies {
    implementation "myGroup:myArtifact1:1.0"
}
        """

        libEntriesInClasspathFileHaveFilenames(artifact1.name)
    }

    @Test
    @ToBeFixedForConfigurationCache
    void respectsPerDependencyExcludes() {
        def artifact1 = mavenRepo.module("myGroup", "myArtifact1", "1.0").dependsOn("myGroup", "myArtifact2", "1.0").publish().artifactFile
        mavenRepo.module("myGroup", "myArtifact2", "1.0").publish()

        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url = "${mavenRepo.uri}" }
}

dependencies {
    implementation("myGroup:myArtifact1:1.0") {
        exclude module: "myArtifact2"
    }
}
        """

        libEntriesInClasspathFileHaveFilenames(artifact1.name)
    }

    private void checkIsWrittenWithUtf8Encoding(File file) {
        def text = file.getText("UTF-8")
        assert text.contains('encoding="UTF-8"')
        String expectedNonAsciiChars = "\u7777\u8888\u9999"
        assert text.contains(expectedNonAsciiChars)
    }

    @Test
    @ToBeFixedForConfigurationCache
    void addsLinkToTheProjectFile() {
        runEclipseTask '''
apply plugin: 'java'
apply plugin: 'eclipse'

eclipse.project {
    linkedResource name: 'one', type: '2', location: '/xyz'
    linkedResource name: 'two', type: '3', locationUri: 'file://xyz'
}
'''

        def xml = parseProjectFile()
        assert xml.linkedResources.link[0].name.text() == 'one'
        assert xml.linkedResources.link[0].type.text() == '2'
        assert xml.linkedResources.link[0].location.text() == '/xyz'

        assert xml.linkedResources.link[1].name.text() == 'two'
        assert xml.linkedResources.link[1].type.text() == '3'
        assert xml.linkedResources.link[1].locationURI.text() == 'file://xyz'
    }

    @Test
    @ToBeFixedForConfigurationCache
    void allowsConfiguringJavaVersionWithSimpleTypes() {
        runEclipseTask '''
apply plugin: 'java'
apply plugin: 'eclipse'

eclipse.jdt {
    sourceCompatibility = '1.4'
    targetCompatibility = 1.3
}
'''

        def jdt = parseJdtFile()
        assert jdt.contains('source=1.4')
        assert jdt.contains('targetPlatform=1.3')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void sourceAndTargetCompatibilityDefaultIsCurrentJavaVersion() {
        runEclipseTask '''
apply plugin: 'java'
apply plugin: 'eclipse'
'''
        def jdt = parseJdtFile()
        def javaVersion = JavaVersion.current()
        def javaVersionNumber = javaVersion.isJava9Compatible() ? javaVersion.getMajorVersion() : javaVersion.toString()
        assert jdt.contains('source=' + javaVersionNumber)
        assert jdt.contains('targetPlatform=' + javaVersionNumber)
    }

    @Test
    @ToBeFixedForConfigurationCache
    void sourceAndTargetCompatibilityDefinedInPluginConvention() {
        runEclipseTask '''
apply plugin: 'java'
apply plugin: 'eclipse'
java {
    sourceCompatibility = 1.4
    targetCompatibility = 1.3
}
'''
        def jdt = parseJdtFile()
        assert jdt.contains('source=1.4')
        assert jdt.contains('targetPlatform=1.3')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void jdtSettingsHasPrecedenceOverJavaPluginConvention() {
        runEclipseTask '''
apply plugin: 'java'
apply plugin: 'eclipse'
java {
    sourceCompatibility = 1.4
    targetCompatibility = 1.5
}
eclipse {
    jdt {
        sourceCompatibility = 1.3
        targetCompatibility = 1.4
    }
}
'''
        def jdt = parseJdtFile()
        assert jdt.contains('source=1.3')
        assert jdt.contains('targetPlatform=1.4')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void dslAllowsShortFormsForProject() {
        runEclipseTask '''
apply plugin: 'java'
apply plugin: 'eclipse'

eclipse.project.name = 'x'
assert eclipse.project.name == 'x'

eclipse {
    project.name += 'x'
    assert project.name == 'xx'
}

eclipse.project {
    name += 'x'
    assert name == 'xxx'
}

'''
    }

    @Test
    @ToBeFixedForConfigurationCache
    void dslAllowsShortForms() {
        runEclipseTask '''
apply plugin: 'java'
apply plugin: 'eclipse'

eclipse.classpath.downloadSources = false
assert eclipse.classpath.downloadSources == false

eclipse.classpath.file.withXml {}
eclipse.classpath {
    file.withXml {}
}
eclipse {
    classpath.file.withXml {}
}
'''
    }

    @Test
    @Issue("GRADLE-1157")
    @ToBeFixedForConfigurationCache
    void canHandleDependencyWithoutSourceJarInFlatDirRepo() {
        def repoDir = testDirectory.createDir("repo")
        repoDir.createFile("lib-1.0.jar")

        runEclipseTask """
apply plugin: "java"
apply plugin: "eclipse"

repositories {
	flatDir { dirs "${TextUtil.escapeString(repoDir)}" }
}

dependencies {
	implementation "some:lib:1.0"
}
        """
    }

    @Test
    @Issue("GRADLE-1706") // doesn't prove that the issue is fixed because the test also passes with 1.0-milestone-4
    @ToBeFixedForConfigurationCache
    void canHandleDependencyWithoutSourceJarInMavenRepo() {
        mavenRepo.module("some", "lib", "1.0").publish()

        runEclipseTask """
apply plugin: "java"
apply plugin: "eclipse"

repositories {
    maven { url = "${mavenRepo.uri}" }
}

dependencies {
	implementation "some:lib:1.0"
}
        """
    }

    void assertHasExpectedContents(TestFile actualFile, String expectedFileName) {
        actualFile.assertExists()
        TestFile expectedFile = testDirectory.file("expectedFiles/$expectedFileName").assertIsFile()
        String expectedXml = expectedFile.text
        String actualXml = getActualXml(actualFile)
        Diff diff = new Diff(expectedXml, actualXml)

        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier())
        try {
            XMLAssert.assertXMLEqual(diff, true)
        } catch (AssertionFailedError error) {
            println "EXPECTED:\n${expectedXml}"
            println "ACTUAL:\n${actualXml}"
            throw new ComparisonFailure("Comparison failure: expected: $expectedFile, actual: $actualFile"
                + "\nUnexpected content for generated actualFile: ${error.message}", expectedXml, actualXml).initCause(error)
        }
    }

    void assertHasExpectedProperties(TestFile actualFile, String expectedFileName) {
        actualFile.assertExists()
        TestFile expectedFile = testDirectory.file("expectedFiles/$expectedFileName").assertIsFile()
        Properties expected = new Properties()
        expected.load(new ByteArrayInputStream(expectedFile.bytes))
        Properties actual = new Properties()
        actual.load(new ByteArrayInputStream(actualFile.bytes))
        assert expected == actual
    }

    String getActualXml(File file) {
        def gradleUserHomeDir = executer.getGradleUserHomeDir()
        def homeDir = gradleUserHomeDir.absolutePath.replace(File.separator, '/')
        def pattern = Pattern.compile(Pattern.quote(homeDir) + "/caches/${CacheLayout.MODULES.getKey()}/${CacheLayout.FILE_STORE.getKey()}/([^/]+/[^/]+/[^/]+)/[a-z0-9]+/")
        def text = file.text.replaceAll(pattern, '@CACHE_DIR@/$1/@SHA1@/')
        pattern = Pattern.compile("GRADLE_USER_HOME/${CacheLayout.MODULES.getKey()}/${CacheLayout.FILE_STORE.getKey()}/([^/]+/[^/]+/[^/]+)/[a-z0-9]+/")
        text = text.replaceAll(pattern, 'GRADLE_USER_HOME/@CACHE@/$1/@SHA1@/')

        //remove trailing slashes for windows paths
        text = text.replaceAll("jar:file:/", 'jar:file:')
        return text
    }

}
