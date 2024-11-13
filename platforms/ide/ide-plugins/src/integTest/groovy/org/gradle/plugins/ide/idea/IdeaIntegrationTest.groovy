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

import groovy.xml.XmlSlurper
import junit.framework.AssertionFailedError
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier
import org.custommonkey.xmlunit.XMLAssert
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.plugins.ide.AbstractIdeIntegrationTest
import org.gradle.test.fixtures.file.TestFile
import org.junit.ComparisonFailure
import org.junit.Rule
import org.junit.Test

import java.util.regex.Pattern

class IdeaIntegrationTest extends AbstractIdeIntegrationTest implements StableConfigurationCacheDeprecations {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Test
    @ToBeFixedForConfigurationCache
    void mergesMetadataFilesCorrectly() {
        file("settings.gradle") << """
            rootProject.name = "master"
        """
        def buildFile = file("build.gradle")
        buildFile << """
            plugins {
                id("java")
                id("idea")
            }
        """

        //given
        executer.withTasks('idea').run()
        def projectContent = getFile([:], 'master.ipr').text
        def moduleContent = getFile([:], 'master.iml').text

        executer.withTasks('idea').run()
        def projectContentAfterMerge = getFile([:], 'master.ipr').text
        def moduleContentAfterMerge = getFile([:], 'master.iml').text

        //then
        assert projectContent == projectContentAfterMerge
        assert moduleContent == moduleContentAfterMerge
    }

    @Test
    @ToBeFixedForConfigurationCache
    void canCreateAndDeleteMetaData() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.ipr')
        assertHasExpectedContents('root.iws')
        assertHasExpectedContents('root.iml')
        assertHasExpectedContents('api/api.iml')
        assertHasExpectedContents('webservice/webservice.iml')
        expectTaskGetProjectDeprecations(3)
        executer.withTasks('cleanIdea').run()
    }

    @Test
    @ToBeFixedForConfigurationCache
    void worksWithAnEmptyProject() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.ipr')
        assertHasExpectedContents('root.iml')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void worksWithASubProjectThatDoesNotHaveTheIdeaPluginApplied() {
        createDirs("a", "b")
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.ipr')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void worksWithNonStandardLayout() {
        createDirs("a child project")
        executer.inDirectory(testDirectory.file('root')).withTasks('idea').run()

        assertHasExpectedContents('root/root.ipr')
        assertHasExpectedContents('root/root.iml')
        assertHasExpectedContents('top-level.iml')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void overwritesExistingDependencies() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.iml')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void addsScalaSdkAndCompilerLibraries() {
        executer.withTasks('idea').run()

        hasProjectLibrary('root.ipr', 'scala-sdk-2.10.0', [], [], [], ['compiler-bridge_2.10', 'scala-library-2.10.0', 'scala-compiler-2.10.0', 'scala-reflect-2.10.0', 'compiler-interface', 'util-interface'])
        hasProjectLibrary('root.ipr', 'scala-sdk-2.11.2', [], [], [], ['compiler-bridge_2.11', 'scala-library-2.11.2', 'scala-compiler-2.11.2', 'scala-reflect-2.11.2', 'scala-xml_2.11-1.0.2', 'scala-parser-combinators_2.11-1.0.2', 'compiler-interface', 'util-interface'])
        def scalaLibs = [
            'scala3-compiler_3-3.0.1.', 'scala3-sbt-bridge-3.0.1.', 'scala3-interfaces-3.0.1.', 'tasty-core_3-3.0.1.',
            'scala3-library_3-3.0.1.', 'scala-asm-9.1.0-scala-1', 'compiler-interface-1.3.5', 'jline-reader-3.19.0.',
            'jline-terminal-jna-3.19.0', 'jline-terminal-3.19.0.', 'scala-library-2.13.6', 'protobuf-java-3.7.0.', 'util-interface', 'jna-5.3.1'
        ]
        def scaladocLibsAndDeps = [
            'scaladoc_3-3.0.1.', 'scala3-tasty-inspector_3-3.0.1', 'flexmark-0', 'flexmark-html-parser', 'flexmark-ext-anchorlink',
            'flexmark-ext-autolink', 'flexmark-ext-emoji', 'flexmark-ext-gfm-strikethrough', 'flexmark-ext-gfm-tables',
            'flexmark-ext-gfm-tasklist', 'flexmark-ext-wikilink', 'flexmark-ext-yaml-front-matter', 'liqp', 'jsoup', 'jackson-dataformat-yaml',
            'flexmark-util', 'flexmark-formatter', 'autolink-0.6', 'flexmark-jira-converter', 'antlr-3', 'jackson-annotations', 'jackson-core',
            'jackson-databind', 'snakeyaml', 'flexmark-ext-tables', 'flexmark-ext-ins', 'flexmark-ext-superscript', 'antlr-runtime', 'ST4'
        ]
        hasProjectLibrary('root.ipr', 'scala-sdk-3.0.1', [], [], [], scalaLibs + scaladocLibsAndDeps)
        hasScalaSdk('project1/project1.iml', '2.11.2')
        hasScalaSdk('project2/project2.iml', '2.10.0')
        hasScalaSdk('project3/project3.iml', '2.11.2')
        hasScalaSdk('project4/project4.iml', '3.0.1')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void addsScalaFacetAndCompilerLibraries() {
        executer.withTasks('idea').run()

        hasProjectLibrary('root.ipr', 'scala-compiler-2.10.0', ['compiler-bridge_2.10', 'scala-compiler-2.10.0', 'scala-library-2.10.0', 'scala-reflect-2.10.0', 'compiler-interface', 'util-interface'], [], [], [])
        hasProjectLibrary('root.ipr', 'scala-compiler-2.11.2', ['compiler-bridge_2.11', 'scala-library-2.11.2', 'scala-compiler-2.11.2', 'scala-reflect-2.11.2', 'scala-xml_2.11-1.0.2', 'scala-parser-combinators_2.11-1.0.2', 'compiler-interface', 'util-interface'], [], [], [])
        def scalaLibs = [
            'scala3-compiler_3-3.0.1.', 'scala3-sbt-bridge-3.0.1.', 'scala3-interfaces-3.0.1.', 'tasty-core_3-3.0.1.',
            'scala3-library_3-3.0.1.', 'scala-asm-9.1.0-scala-1', 'compiler-interface-1.3.5', 'jline-reader-3.19.0.',
            'jline-terminal-jna-3.19.0', 'jline-terminal-3.19.0.', 'scala-library-2.13.6', 'protobuf-java-3.7.0.', 'util-interface', 'jna-5.3.1'
        ]
        def scaladocLibsAndDeps = [
            'scaladoc_3-3.0.1.', 'scala3-tasty-inspector_3-3.0.1', 'flexmark-0', 'flexmark-html-parser', 'flexmark-ext-anchorlink',
            'flexmark-ext-autolink', 'flexmark-ext-emoji', 'flexmark-ext-gfm-strikethrough', 'flexmark-ext-gfm-tables',
            'flexmark-ext-gfm-tasklist', 'flexmark-ext-wikilink', 'flexmark-ext-yaml-front-matter', 'liqp', 'jsoup', 'jackson-dataformat-yaml',
            'flexmark-util', 'flexmark-formatter', 'autolink-0.6', 'flexmark-jira-converter', 'antlr-3', 'jackson-annotations', 'jackson-core',
            'jackson-databind', 'snakeyaml', 'flexmark-ext-tables', 'flexmark-ext-ins', 'flexmark-ext-superscript', 'antlr-runtime', 'ST4'
        ]
        hasProjectLibrary('root.ipr', 'scala-compiler-3.0.1',  scalaLibs + scaladocLibsAndDeps, [], [], [])
        hasScalaFacet('project1/project1.iml', 'scala-compiler-2.11.2')
        hasScalaFacet('project2/project2.iml', 'scala-compiler-2.10.0')
        hasScalaFacet('project3/project3.iml', 'scala-compiler-2.11.2')
        hasScalaFacet('project4/project4.iml', 'scala-compiler-3.0.1')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void outputDirsDefaultToToIdeaDefaults() {
        runIdeaTask("apply plugin: 'java'; apply plugin: 'idea'")

        def module = parseImlFile("root")
        assert module.component.@"inherit-compiler-output" == "true"
    }

    @Test
    @ToBeFixedForConfigurationCache
    void canHandleCircularModuleDependencies() {
        def repoDir = file("repo")
        def artifact1 = maven(repoDir).module("myGroup", "myArtifact1").dependsOnModules("myArtifact2").publish().artifactFile
        def artifact2 = maven(repoDir).module("myGroup", "myArtifact2").dependsOnModules("myArtifact1").publish().artifactFile

        runIdeaTask """
apply plugin: "java"
apply plugin: "idea"

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    implementation "myGroup:myArtifact1:1.0"
}
        """

        def module = parseImlFile("root")
        def libs = module.component.orderEntry.library
        assert libs.size() == 2
        assert libs.CLASSES.root*.@url*.text().collect { new File(it).name } as Set == [artifact1.name + "!", artifact2.name + "!"] as Set
    }

    @Test
    @ToBeFixedForConfigurationCache
    void libraryReferenceSubstitutesPathVariable() {
        def repoDir = file("repo")
        def artifact1 = maven(repoDir).module("myGroup", "myArtifact1").publish().artifactFile

        runIdeaTask """
    apply plugin: "java"
    apply plugin: "idea"

    repositories {
        maven { url "${repoDir.toURI()}" }
    }

    idea {
       pathVariables("GRADLE_REPO": file("repo"))
    }

    dependencies {
        implementation "myGroup:myArtifact1:1.0"
    }
            """

        def module = parseImlFile("root")
        def libs = module.component.orderEntry.library
        assert libs.size() == 1
        assert libs.CLASSES.root*.@url*.text().collect { new File(it).name } as Set == [artifact1.name + "!"] as Set
        assert libs.CLASSES.root*.@url*.text().findAll() { it.contains("\$GRADLE_REPO\$") }.size() == 1
        assert libs.CLASSES.root*.@url*.text().collect { it.replace("\$GRADLE_REPO\$", relPath(repoDir)) } as Set == ["jar://${relPath(artifact1)}!/"] as Set
    }

    @Test
    @ToBeFixedForConfigurationCache
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
    @ToBeFixedForConfigurationCache
    void triggersWithXmlConfigurationHooks() {
        runIdeaTask '''
apply plugin: 'java'
apply plugin: 'idea'

def hookActivated = 0

idea.module.iml {
    withXml { hookActivated++ }
}

tasks.idea {
    doLast {
        assert hookActivated == 1 : "withXml() hook should be fired"
    }
}
'''
    }

    @Test
    @ToBeFixedForConfigurationCache
    void respectsPerConfigurationExcludes() {
        def repoDir = file("repo")
        maven(repoDir).module("myGroup", "myArtifact1").dependsOnModules("myArtifact2").publish()
        maven(repoDir).module("myGroup", "myArtifact2").publish()

        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url "${repoDir.toURI()}" }
}

configurations {
    implementation.exclude module: 'myArtifact2'
}

dependencies {
    implementation "myGroup:myArtifact1:1.0"
}
        """

        def module = parseImlFile("root")
        def libs = module.component.orderEntry.library
        assert libs.size() == 1
    }

    @Test
    @ToBeFixedForConfigurationCache
    void respectsPerDependencyExcludes() {
        def repoDir = file("repo")
        maven(repoDir).module("myGroup", "myArtifact1").dependsOnModules("myArtifact2").publish()
        maven(repoDir).module("myGroup", "myArtifact2").publish()

        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    implementation("myGroup:myArtifact1:1.0") {
        exclude module: "myArtifact2"
    }
}
        """

        def module = parseImlFile("root")
        def libs = module.component.orderEntry.library
        assert libs.size() == 1
    }

    @Test
    @ToBeFixedForConfigurationCache
    void allowsCustomOutputFolders() {
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

idea.module {
    inheritOutputDirs = false
    outputDir = file('foo-out')
    testOutputDir = file('foo-out-test')
}
"""

        //then
        def iml = getFile([:], 'root.iml').text
        assert iml.contains('inherit-compiler-output="false"')
        assert iml.contains('foo-out')
        assert iml.contains('foo-out-test')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void dslSupportsShortFormsForModule() {
        runTask('idea', """
apply plugin: 'idea'

idea.module.name = 'X'
assert idea.module.name == 'X'

idea {
    module.name += 'X'
    assert module.name == 'XX'
}

idea.module {
    name += 'X'
    assert name == 'XXX'
}

""")
    }

    @Test
    @ToBeFixedForConfigurationCache
    void dslSupportsShortFormsForProject() {
        runTask('idea', """
apply plugin: 'idea'

idea.project.wildcards = ['1'] as Set
assert idea.project.wildcards == ['1'] as Set

idea {
    project.wildcards += '2'
    assert project.wildcards == ['1', '2'] as Set
}

idea.project {
    wildcards += '3'
    assert wildcards == ['1', '2', '3'] as Set
}

""")
    }

    @Test
    @ToBeFixedForConfigurationCache
    void showDecentMessageWhenInputFileWasTinkeredWith() {
        //given
        file('root.iml') << 'messed up iml file'

        file('build.gradle') << '''
apply plugin: "java"
apply plugin: "idea"
'''
        file('settings.gradle') << 'rootProject.name = "root"'

        //when
        def failure = executer.withTasks('idea').runWithFailure()

        //then
        failure.output.contains("Perhaps this file was tinkered with?")
    }

    @Test
    @ToBeFixedForConfigurationCache
    void hasDefaultProjectLanguageLevelIfNoJavaPluginApplied() {
        //given
        file('build.gradle') << '''
apply plugin: "idea"
'''
        file('settings.gradle') << 'rootProject.name = "root"'

        //when
        executer.withTasks('idea').run()

        //then
        assertProjectLanguageLevel("root.ipr", "JDK_1_6")
    }

    void assertProjectLanguageLevel(String iprFileName, String javaVersion) {
        def project = new XmlSlurper().parse(file(iprFileName))
        def projectRootMngr = project.component.find { it.@name == "ProjectRootManager" }
        assert projectRootMngr
        assert projectRootMngr.@languageLevel == javaVersion
    }

    @Test
    @ToBeFixedForConfigurationCache
    void canAddProjectLibraries() {
        runTask("idea", """
apply plugin: 'idea'

idea.project {
    def lib = new org.gradle.plugins.ide.idea.model.ProjectLibrary()
    lib.name = "someLib"
    lib.classes << file("someClasses.jar")
    lib.javadoc << file("someJavadoc.jar")
    lib.sources << file("someSources.jar")
    projectLibraries << lib
}
""")

        hasProjectLibrary("root.ipr", "someLib", ["someClasses.jar"], ["someJavadoc.jar"], ["someSources.jar"], [])
    }

    private void assertHasExpectedContents(String path) {
        TestFile actualFile = testDirectory.file(path).assertIsFile()
        TestFile expectedFile = testDirectory.file("expectedFiles/${path}.xml").assertIsFile()

        def expectedXml = expectedFile.text

        def homeDir = executer.gradleUserHomeDir.absolutePath.replace(File.separator, '/')
        def pattern = Pattern.compile(Pattern.quote(homeDir) + "/caches/${CacheLayout.MODULES.getKey()}/${CacheLayout.FILE_STORE.getKey()}/([^/]+/[^/]+/[^/]+)/[a-z0-9]+/")
        def actualXml = actualFile.text.replaceAll(pattern, '@CACHE_DIR@/$1/@SHA1@/')

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

    private void hasProjectLibrary(String iprFileName, String libraryName, List<String> classesLibs, List<String> javadocLibs, List<String> sourcesLibs, List<String> compilerClasses) {
        def project = new XmlSlurper().parse(file(iprFileName))
        def libraryTable = project.component.find { it.@name == "libraryTable" }
        assert libraryTable

        def library = libraryTable.library.find { it.@name == libraryName }
        assert library: "Can't find $libraryName in ${libraryTable.library.@name.join(', ')}"

        def classesRoots = library.CLASSES.root
        assert classesRoots.size() == classesLibs.size()
        classesLibs.each {
            assert classesRoots.@url.text().contains(it)
        }

        def javadocRoots = library.JAVADOC.root
        assert javadocRoots.size() == javadocLibs.size()
        javadocLibs.each {
            assert javadocRoots.@url.text().contains(it)
        }

        def sourcesRoots = library.SOURCES.root
        assert sourcesRoots.size() == sourcesLibs.size()
        sourcesLibs.each {
            assert sourcesRoots.@url.text().contains(it)
        }

        def compilerClasspathRoots = library.properties[0].'compiler-classpath'[0].root
        assert compilerClasspathRoots.@url.list().size() == compilerClasses.size()
        compilerClasses.each {
            assert compilerClasspathRoots.@url.text().contains(it)
        }
    }

    private void hasScalaSdk(String imlFileName, String version) {
        def module = new XmlSlurper().parse(file(imlFileName))
        def newModuleRootManager = module.component.find { it.@name == "NewModuleRootManager" }
        assert newModuleRootManager

        def sdkLibrary = newModuleRootManager.orderEntry.find { it.@name == "scala-sdk-$version" }
        assert sdkLibrary
        assert sdkLibrary.@type == "library"
        assert sdkLibrary.@level == "project"
    }

    private void hasScalaFacet(String imlFileName, String libraryName) {
        def module = new XmlSlurper().parse(file(imlFileName))
        def facetManager = module.component.find { it.@name == "FacetManager" }
        assert facetManager

        def facet = facetManager.facet.find { it.@name == "Scala" }
        assert facet
        assert facet.@type == "scala"

        def compilerLibraryLevel = facet.configuration.option.find { it.@name == "compilerLibraryLevel" }
        assert compilerLibraryLevel
        assert compilerLibraryLevel.@value == "Project"

        def compilerLibraryName = facet.configuration.option.find { it.@name == "compilerLibraryName" }
        assert compilerLibraryName
        assert compilerLibraryName.@value == libraryName
    }

    private containsDir(path, urls) {
        urls.any { it.endsWith(path) }
    }

    private String relPath(File file) {
        return file.absolutePath.replace(File.separator, "/")
    }
}
