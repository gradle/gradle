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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

class EclipseClasspathIntegrationTest extends AbstractEclipseIntegrationTest {

    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    String content

    private final String jreContainerPath = "org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-${JavaVersion.current().isJava9Compatible() ? JavaVersion.current().getMajorVersion() : JavaVersion.current()}/"

    @Test
    @ToBeFixedForConfigurationCache
    void classpathContainsLibraryEntriesForExternalAndFileDependencies() {
        //given
        def module = mavenRepo.module('coolGroup', 'niceArtifact', '1.0')
        module.artifact(classifier: 'sources')
        module.artifact(classifier: 'javadoc')
        module.publish()
        def jar = module.artifactFile
        def srcJar = module.artifactFile(classifier: 'sources')

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.uri}" }
    ${RepoScriptBlockUtil.mavenCentralRepositoryDefinition()}
}

dependencies {
    implementation 'coolGroup:niceArtifact:1.0'
    implementation 'commons-lang:commons-lang:2.6'
    implementation files('lib/dep.jar')
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 3
        libraries[0].assertHasJar(jar)
        libraries[0].assertHasSource(srcJar)
        libraries[0].assertHasNoJavadoc()
        libraries[1].assertHasCachedJar('commons-lang', 'commons-lang', '2.6')
        libraries[1].assertHasCachedSource('commons-lang', 'commons-lang', '2.6')
        libraries[1].assertHasNoJavadoc()
        libraries[2].assertHasJar(file('lib/dep.jar'))
        libraries[2].assertHasNoSource()
        libraries[2].assertHasNoJavadoc()
    }

    @Test
    @Issue("GRADLE-1945")
    @ToBeFixedForConfigurationCache
    void unresolvedDependenciesAreLogged() {
        //given
        def module = mavenRepo.module('myGroup', 'existing-artifact', '1.0')
        module.publish()

        //when
        ExecutionResult result = runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.uri}" }
}

configurations {
    myPlusConfig
    myMinusConfig
}

dependencies {
    myPlusConfig group: 'myGroup', name: 'missing-extra-artifact', version: '1.0'
    myPlusConfig group: 'myGroup', name: 'filtered-artifact', version: '1.0'
    myMinusConfig group: 'myGroup', name: 'filtered-artifact', version: '1.0'
    runtimeOnly  group: 'myGroup', name: 'missing-artifact', version: '1.0'
    implementation  group: 'myGroup', name: 'existing-artifact', version: '1.0'

    eclipse {
        classpath {
            plusConfigurations += [ configurations.myPlusConfig ]
            minusConfigurations += [ configurations.myMinusConfig]
        }
    }
}
"""
        String expected = """Could not resolve: myGroup:missing-artifact:1.0
Could not resolve: myGroup:missing-extra-artifact:1.0
"""
        result.groupedOutput.task(":eclipseClasspath").output == expected
    }

    @Test
    @Issue("GRADLE-1622")
    @ToBeFixedForConfigurationCache
    void classpathContainsEntriesForDependenciesThatOnlyDifferByClassifier() {
        //given:
        def module = mavenRepo.module('coolGroup', 'niceArtifact', '1.0')
        module.artifact(classifier: 'extra')
        module.artifact(classifier: 'tests')
        module.publish()
        def baseJar = module.artifactFile
        def extraJar = module.artifactFile(classifier: 'extra')
        def testsJar = module.artifactFile(classifier: 'tests')
        def anotherJar = mavenRepo.module('coolGroup', 'another', '1.0').publish().artifactFile

        //when:
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.uri}" }
}

dependencies {
    implementation 'coolGroup:niceArtifact:1.0'
    implementation 'coolGroup:niceArtifact:1.0:extra'
    testImplementation 'coolGroup:another:1.0'
    testImplementation 'coolGroup:niceArtifact:1.0:tests'
}
"""

        //then:
        def libraries = classpath.libs
        assert libraries.size() == 4
        libraries[0].assertHasJar(baseJar)
        libraries[1].assertHasJar(extraJar)
        libraries[2].assertHasJar(testsJar)
        libraries[3].assertHasJar(anotherJar)
    }

    @Test
    @ToBeFixedForConfigurationCache
    void includesTransitiveRepoFileDependencies() {
        //given
        def someArtifactJar = mavenRepo.module('someGroup', 'someArtifact', '1.0').publish().artifactFile
        def someOtherArtifactJar = mavenRepo.module('someGroup', 'someOtherArtifact', '1.0').publish().artifactFile

        //when
        createDirs("a", "b", "c")
        runEclipseTask """include 'a', 'b', 'c'""", """
subprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'

    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

configure(project(":a")){
    dependencies {
        implementation 'someGroup:someOtherArtifact:1.0'

        implementation project(':b')
    }
}

configure(project(":b")){
    dependencies {
        implementation project(':c')
    }
}

configure(project(":c")){
    dependencies {
        implementation 'someGroup:someArtifact:1.0'
    }
}
"""

        def libs = classpath("a").libs
        assert classpath("a").projects.collect { it.name } == ["b", "c"]
        assert libs.size() == 2
        libs[0].assertHasJar(someOtherArtifactJar)
        libs[1].assertHasJar(someArtifactJar)
    }

    @Test
    @ToBeFixedForConfigurationCache
    void includesTransitiveImplementationDependencies() {
        //given
        def someArtifactJar = mavenRepo.module('someGroup', 'someArtifact', '1.0').publish().artifactFile
        def someOtherArtifactJar = mavenRepo.module('someGroup', 'someOtherArtifact', '1.0').publish().artifactFile

        //when
        createDirs("a", "b", "c")
        runEclipseTask """include 'a', 'b', 'c'""", """
subprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'

    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

configure(project(":a")){
    dependencies {
        implementation 'someGroup:someOtherArtifact:1.0'

        implementation project(':b')
    }
}

configure(project(":b")){
    apply plugin: 'java-library'
    dependencies {
        api project(':c')
    }
}

configure(project(":c")){
    apply plugin: 'java-library'
    dependencies {
        implementation 'someGroup:someArtifact:1.0'
    }
}
"""

        def libs = classpath("a").libs
        assert classpath("a").projects.collect { it.name } == ["b", "c"]
        assert libs.size() == 2
        libs[0].assertHasJar(someOtherArtifactJar)
        libs[1].assertHasJar(someArtifactJar)
    }

    @Test
    @ToBeFixedForConfigurationCache
    void transitiveProjectDependenciesMappedAsDirectDependencies() {
        given:
        createDirs("a", "b", "c")
        runEclipseTask """include 'a', 'b', 'c'""", """
subprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'

    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

configure(project(":a")){
    dependencies {
        implementation project(':b')
    }
}

configure(project(":b")){
    dependencies {
        implementation project(':c')
    }
}

"""

        then:
        def eclipseClasspath = classpath("a")
        assert eclipseClasspath.projects.collect { it.name } == ['b', 'c']
    }

    @Test
    @ToBeFixedForConfigurationCache
    void transitiveFileDependenciesMappedAsDirectDependencies() {
        createDirs("a", "b", "c")
        runEclipseTask """include 'a', 'b', 'c'""", """
subprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'

    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

configure(project(":a")){
    dependencies {
        implementation files("bar.jar")
        implementation project(':b')
    }
}

configure(project(":b")){
    dependencies {
        implementation project(':c')
        implementation files("baz.jar")

    }
}

configure(project(":c")){
    dependencies {
        implementation files("foo.jar")
    }
}
"""

        def eclipseClasspath = classpath("a")
        assert eclipseClasspath.projects.collect { it.name } == ['b', 'c']
        eclipseClasspath.libs[0].assertHasJar(file("a/bar.jar"))
        eclipseClasspath.libs[1].assertHasJar(file("b/baz.jar"))
        eclipseClasspath.libs[2].assertHasJar(file("c/foo.jar"))
    }

    @Test
    @ToBeFixedForConfigurationCache
    void classpathContainsConflictResolvedDependencies() {
        def someLib1Jar = mavenRepo.module('someGroup', 'someLib', '1.0').publish().artifactFile
        def someLib2Jar = mavenRepo.module('someGroup', 'someLib', '2.0').publish().artifactFile

        def settingsFile = file("settings.gradle")
        createDirs("a", "b")
        settingsFile << """ include 'a', 'b'"""
        def buildFile = file("build.gradle")
        buildFile << """
subprojects {
    apply plugin: 'java-library'
    apply plugin: 'eclipse'

    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

configure(project(":a")){
    dependencies {
        implementation ('someGroup:someLib') {
            if (project.hasProperty("strictDeps")) {
                version {
                    strictly '1.0'
                }
            }
        }

        implementation project(':b')
    }
}

configure(project(":b")){
    dependencies {
        api 'someGroup:someLib:2.0'
    }
}
"""
        executer.withTasks("eclipse").run()

        def libs = classpath("a").libs
        assert classpath("a").projects.collect { it.name } == ['b']
        assert libs.size() == 1
        libs[0].assertHasJar(someLib2Jar)

        executer.withArgument("-PstrictDeps=true").withTasks("eclipse").run()

        libs = classpath("a").libs
        assert classpath("a").projects.collect { it.name } == ['b']
        assert libs.size() == 1
        libs[0].assertHasJar(someLib1Jar)
    }


    @Test
    @ToBeFixedForConfigurationCache
    void substitutesPathVariablesIntoLibraryPathsExceptForJavadoc() {
        //given
        def module = mavenRepo.module('coolGroup', 'niceArtifact', '1.0')
        module.artifact(classifier: 'sources')
        module.artifact(classifier: 'javadoc')
        module.publish()

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.uri}" }
}

dependencies {
    implementation 'coolGroup:niceArtifact:1.0'
    implementation files('lib/dep.jar')
}

eclipse {
    pathVariables REPO_DIR: file('${mavenRepo.uri}')
    pathVariables LIB_DIR: file('lib')
    classpath.downloadJavadoc = true
}
"""

        //then
        def libraries = classpath.vars
        assert libraries.size() == 2
        libraries[0].assertHasJar('REPO_DIR/coolGroup/niceArtifact/1.0/niceArtifact-1.0.jar')
        libraries[0].assertHasSource('REPO_DIR/coolGroup/niceArtifact/1.0/niceArtifact-1.0-sources.jar')
        libraries[1].assertHasJar('LIB_DIR/dep.jar')

        //javadoc is not substituted
        libraries[0].assertHasJavadoc(file("maven-repo/coolGroup/niceArtifact/1.0/niceArtifact-1.0-javadoc.jar"))
    }

    @Test
    @ToBeFixedForConfigurationCache
    void canCustomizeTheClasspathModel() {
        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

sourceSets.main.java.srcDirs.each { it.mkdirs() }
sourceSets.main.resources.srcDirs.each { it.mkdirs() }

configurations {
  someConfig
}

dependencies {
  someConfig files('foo.txt')
}

eclipse {

  pathVariables fooPathVariable: projectDir

  classpath {
    sourceSets = []

    plusConfigurations << configurations.someConfig

    containers 'someFriendlyContainer', 'andYetAnotherContainer'

    defaultOutputDir = file('build-eclipse')

    downloadSources = false
    downloadJavadoc = true

    file {
      withXml { it.asNode().appendNode('message', 'be cool') }
    }
  }
}
"""

        //then
        def vars = classpath.vars
        assert vars.size() == 1
        vars[0].assertHasJar("fooPathVariable/foo.txt")

        def containers = classpath.containers
        assert containers.size() == 3
        assert containers[1] == 'someFriendlyContainer'
        assert containers[2] == 'andYetAnotherContainer'

        assert classpath.output == 'build-eclipse'
        assert classpath.classpath.message[0].text() == 'be cool'
    }

    @Issue("GRADLE-3101")
    @Test
    @ToBeFixedForConfigurationCache
    void canCustomizeTheClasspathModelUsingPlusEqual() {
        def module = mavenRepo.module('coolGroup', 'niceArtifact', '1.0')
        module.publish()
        def baseJar = module.artifactFile

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.uri}" }
}

sourceSets.main.java.srcDirs.each { it.mkdirs() }
sourceSets.main.resources.srcDirs.each { it.mkdirs() }

configurations {
  someConfig
}

eclipse {

  classpath {
    sourceSets = []

    plusConfigurations += [ configurations.someConfig ]
  }
}

dependencies {
    someConfig 'coolGroup:niceArtifact:1.0'
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 1
        libraries[0].assertHasJar(baseJar)
    }

    @Test
    @Issue("GRADLE-1487")
    @ToBeFixedForConfigurationCache
    void handlesPlusMinusConfigurationsForSelfResolvingDeps() {
        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

configurations {
  someConfig
  someOtherConfig
}

dependencies {
  someConfig files('foo.txt', 'bar.txt', 'unwanted.txt')
  someOtherConfig files('unwanted.txt')
}

eclipse.classpath {
    plusConfigurations << configurations.someConfig
    minusConfigurations << configurations.someOtherConfig
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 2
        libraries[0].assertHasJar(file("foo.txt"))
        libraries[1].assertHasJar(file("bar.txt"))
    }

    @Test
    @ToBeFixedForConfigurationCache
    void handlesPlusMinusConfigurationsForProjectDeps() {
        //when
        createDirs("foo", "bar", "unwanted")
        runEclipseTask "include 'foo', 'bar', 'unwanted'",
            """
allprojects {
  apply plugin: 'java'
  apply plugin: 'eclipse'
}

configurations {
  someConfig
  someOtherConfig
}

dependencies {
  someConfig project(':foo')
  someConfig project(':bar')
  someConfig project(':unwanted')
  someOtherConfig project(':unwanted')
}

eclipse.classpath {
    plusConfigurations << configurations.someConfig
    minusConfigurations << configurations.someOtherConfig
}
"""

        //then
        assert classpath.projects.collect { it.name } == ['foo', 'bar']
    }

    @Test
    @ToBeFixedForConfigurationCache
    void handlesPlusMinusConfigurationsForExternalDeps() {
        //given
        def jar = mavenRepo.module('coolGroup', 'coolArtifact', '1.0').dependsOn('coolGroup', 'unwantedArtifact', '1.0').publish().artifactFile
        mavenRepo.module('coolGroup', 'unwantedArtifact', '1.0').publish()

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

configurations {
  someConfig
  someOtherConfig
}

repositories {
    maven { url "${mavenRepo.uri}" }
}

dependencies {
  someConfig 'coolGroup:coolArtifact:1.0'
  someOtherConfig 'coolGroup:unwantedArtifact:1.0'
}

eclipse.classpath {
    plusConfigurations << configurations.someConfig
    minusConfigurations << configurations.someOtherConfig
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 1
        libraries[0].assertHasJar(jar)
    }

    @Test
    @ToBeFixedForConfigurationCache
    void canToggleJavadocAndSourcesOn() {
        //given
        def module = mavenRepo.module('coolGroup', 'niceArtifact', '1.0')
        module.artifact(classifier: 'sources')
        module.artifact(classifier: 'javadoc')
        module.publish()
        def jar = module.artifactFile
        def srcJar = module.artifactFile(classifier: 'sources')
        def javadocJar = module.artifactFile(classifier: 'javadoc')

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.uri}" }
}

dependencies {
    implementation 'coolGroup:niceArtifact:1.0'
}

eclipse.classpath {
    downloadSources = true
    downloadJavadoc = true
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 1
        libraries[0].assertHasJar(jar)
        libraries[0].assertHasSource(srcJar)
        libraries[0].assertHasJavadoc(javadocJar)
    }

    @Test
    @ToBeFixedForConfigurationCache
    void canToggleJavadocAndSourcesOff() {
        //given
        def module = mavenRepo.module('coolGroup', 'niceArtifact', '1.0')
        module.artifact(classifier: 'sources')
        module.artifact(classifier: 'javadoc')
        module.publish()
        def jar = module.artifactFile

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.uri}" }
}

dependencies {
    implementation 'coolGroup:niceArtifact:1.0'
}

eclipse.classpath {
    downloadSources = false
    downloadJavadoc = false
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 1
        libraries[0].assertHasJar(jar)
        libraries[0].assertHasNoSource()
        libraries[0].assertHasNoJavadoc()
    }

    @Test
    @ToBeFixedForConfigurationCache
    void removeDependenciesFromExistingClasspathFileWhenMerging() {
        //given
        getClasspathFile() << """<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="output" path="bin"/>
	<classpathentry kind="con" path="$jreContainerPath"/>
	<classpathentry kind="lib" path="/some/path/someDependency.jar"/>
	<classpathentry kind="var" path="SOME_VAR/someVarDependency.jar"/>
	<classpathentry kind="src" path="/someProject"/>
</classpath>
"""

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

dependencies {
  implementation files('newDependency.jar')
}
"""
        //then
        assert classpath.entries.size() == 3
        def libraries = classpath.libs
        assert libraries.size() == 1
        libraries[0].assertHasJar(file('newDependency.jar'))
    }

    @Issue('GRADLE-1953')
    @Test
    @ToBeFixedForConfigurationCache
    void canConstructAndReconstructClasspathFromJavaSourceSets() {
        given:
        def buildFile = file("build.gradle") << """
apply plugin: 'java'
apply plugin: 'eclipse'
"""
        createJavaSourceDirs(buildFile)

        when:
        executer.withTasks('eclipseClasspath').run()

        then:
        assert classpath.entries.size() == 4
        assert classpath.sources.size() == 2

        when:
        executer.withTasks('eclipseClasspath').run()

        then:
        assert classpath.entries.size() == 4
        assert classpath.sources.size() == 2
    }

    @Issue('GRADLE-3335')
    @Test
    @ToBeFixedForConfigurationCache
    void handlesExcludeOnSharedSourceFolders() {
        given:
        def buildFile = file("build.gradle") << """
apply plugin: 'java'
apply plugin: 'eclipse'

sourceSets {
    main {
        java {
            srcDirs = ['src']
        }
        resources{
            srcDirs = ['src']
            exclude '**/*.java'
        }
    }
}
"""
        buildFile.parentFile.file("src").createDir()

        when:
        executer.withTasks('eclipseClasspath').run()

        then:
        assert classpath.entries.size() == 3
        assert classpath.sources.size() == 1
        assert classpath.entries.find { it.@kind == 'src' }.attribute("excluding") == null

        when:
        buildFile.text = """
apply plugin: 'java'
apply plugin: 'eclipse'

sourceSets {
    main {
        java {
            srcDirs = ['src']
            exclude '**/*.xml'
        }
        resources{
            srcDirs = ['src']
            exclude '**/*.xml'
        }
    }
}
"""
        when:
        executer.withTasks('cleanEclipseClasspath', 'eclipseClasspath').run()
        then:
        assert classpath.entries.size() == 3
        assert classpath.sources.size() == 1
        assert classpath.entries.find { it.@kind == 'src' }.attribute("excluding") == "**/*.xml"
    }

    @Test
    @ToBeFixedForConfigurationCache
    void handlesIncludesOnSharedSourceFolders() {
        given:
        def buildFile = file("build.gradle") << """
apply plugin: 'java'
apply plugin: 'eclipse'

sourceSets {
    main {
        java {
            srcDirs = ['src']
            include '**/*.java'
        }
        resources{
            srcDirs = ['src']
            include '**/*.properties'
        }
    }
}
"""
        buildFile.parentFile.file("src").createDir()

        when:
        executer.withTasks('eclipseClasspath').run()

        then:
        assert classpath.entries.size() == 3
        assert classpath.sources.size() == 1
        assert classpath.entries.find { it.@kind == 'src' }.attribute("including") == "**/*.properties|**/*.java"


        when:
        buildFile.text = """
apply plugin: 'java'
apply plugin: 'eclipse'

sourceSets {
    main {
        java {
            srcDirs = ['src']
            include '**/*.java'
        }
        resources{
            srcDirs = ['src']
        }
    }
}
"""
        buildFile.parentFile.file("src").createDir()

        when:
        executer.withTasks('cleanEclipse', 'eclipseClasspath').run()

        then:
        assert classpath.entries.size() == 3
        assert classpath.sources.size() == 1
        assert classpath.entries.find { it.@kind == 'src' }.attribute("including") == null
    }

    @Test
    @ToBeFixedForConfigurationCache
    void canAccessXmlModelBeforeAndAfterGeneration() {
        //given
        def classpath = getClasspathFile([:])
        classpath << """<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="output" path="bin"/>
	<classpathentry kind="con" path="$jreContainerPath"/>
	<classpathentry kind="lib" path="/some/path/someDependency.jar"/>
	<classpathentry kind="var" path="SOME_VAR/someVarDependency.jar"/>
</classpath>
"""

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

def hooks = []

dependencies {
  implementation files('newDependency.jar')
}

eclipse {
  classpath {
    file {
      beforeMerged {
        hooks << 'beforeMerged'
        assert it.entries.size() == 4
        assert it.entries.any { it.path.contains('someDependency.jar') }
        assert it.entries.any { it.path.contains('someVarDependency.jar') }
        assert !it.entries.any { it.path.contains('newDependency.jar') }
      }
      whenMerged {
        hooks << 'whenMerged'
        assert it.entries.size() == 3
        assert it.entries.any { it.path.contains('newDependency.jar') }
        assert !it.entries.any { it.path.contains('someDependency.jar') }
        assert !it.entries.any { it.path.contains('someVarDependency.jar') }
      }
    }
  }
}

eclipseClasspath.doLast() {
  assert hooks == ['beforeMerged', 'whenMerged']
}
"""

        //then no exception is thrown
    }

    @Issue("GRADLE-1502")
    @Test
    @ToBeFixedForConfigurationCache
    void createsLinkedResourcesForSourceDirectoriesWhichAreNotUnderTheProjectDirectory() {
        file('someGroovySrc').mkdirs()

        def settingsFile = file('settings.gradle')
        createDirs("api")
        settingsFile << "include 'api'"

        def buildFile = file('build.gradle')
        buildFile << """
allprojects {
  apply plugin: 'java'
  apply plugin: 'eclipse'
  apply plugin: 'groovy'
}

project(':api') {
    sourceSets {
        main {
            groovy.srcDirs = ['../someGroovySrc']
        }
    }
}
"""
        //when
        executer.withTasks('eclipse').run()

        //then
        def classpath = getClasspathFile(project: 'api').text

        assert !classpath.contains('path="../someGroovySrc"'): "external src folders are not supported by eclipse"
        assert classpath.contains('path="someGroovySrc"'): "external folder should be mapped to linked resource"

        def project = parseProjectFile(project: 'api')

        assert project.linkedResources.link.location.text().contains('someGroovySrc'): 'should contain linked resource for folder that is not beneath the project dir'
    }

    @Issue("GRADLE-1402")
    @Test
    @ToBeFixedForConfigurationCache
    void shouldNotPutSourceSetsOutputDirOnClasspath() {
        testFile('build/generated/main/prod.resource').createFile()
        testFile('build/generated/test/test.resource').createFile()

        //when
        runEclipseTask '''
apply plugin: "java"
apply plugin: "eclipse"

sourceSets.main.output.dir "$buildDir/generated/main"
sourceSets.test.output.dir "$buildDir/generated/test"
'''
        //then
        def libraries = classpath.libs
        assert libraries.size() == 2
        libraries[0].assertHasJar(file('build/generated/main'))
        libraries[1].assertHasJar(file('build/generated/test'))
    }

    @Test
    @ToBeFixedForConfigurationCache
    void theBuiltByTaskBeExecuted() {
        //when
        def result = runEclipseTask('''
apply plugin: "java"
apply plugin: "eclipse"

sourceSets.main.output.dir "$buildDir/generated/main", builtBy: 'generateForMain'
sourceSets.test.output.dir "$buildDir/generated/test", builtBy: 'generateForTest'

task generateForMain
task generateForTest
''')
        //then
        result.assertTasksExecuted(':generateForMain', ':generateForTest', ':eclipseClasspath', ':eclipseJdt', ':eclipseProject', ':eclipse')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void doesNotBreakWhenSomeDependenciesCannotBeResolved() {
        //given
        def repoJar = mavenRepo.module('coolGroup', 'niceArtifact', '1.0').publish().artifactFile
        def localJar = file('someDependency.jar').createFile()

        createDirs("someApiProject")
        file("settings.gradle") << "include 'someApiProject'\n"

        //when
        runEclipseTask """
allprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'
}

repositories {
    maven { url "${mavenRepo.uri}" }
}

dependencies {
    implementation 'coolGroup:niceArtifact:1.0'
    implementation project(':someApiProject')
    implementation 'i.dont:Exist:1.0'
    implementation files('someDependency.jar')
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 3
        libraries[0].assertHasJar(repoJar)
        libraries[1].assertHasJar(localJar)
        libraries[2].assertHasJar(file('unresolved dependency - i.dont Exist 1.0'))
    }

    @Test
    @ToBeFixedForConfigurationCache
    void addsScalaIdeClasspathContainerAndRemovesLibrariesDuplicatedByContainer() {
        //given
        def otherLib = mavenRepo.module('other', 'lib', '3.0').publish().artifactFile

        //when
        runEclipseTask """
apply plugin: 'scala'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.uri}" }
    ${RepoScriptBlockUtil.mavenCentralRepositoryDefinition()}
}

dependencies {
    implementation "org.scala-lang:scala-library:2.9.2"
    runtimeOnly "org.scala-lang:scala-swing:2.9.1"
    testImplementation "org.scala-lang:scala-dbc:2.9.0"
    testRuntimeOnly "other:lib:3.0"
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 1
        libraries[0].assertHasJar(otherLib)
        assert classpath.containers == [jreContainerPath, 'org.scala-ide.sdt.launching.SCALA_CONTAINER']
    }

    @Test
    @ToBeFixedForConfigurationCache
    void avoidsDuplicateJreContainersInClasspathWhenMerging() {
        //given
        getClasspathFile() << """<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="output" path="bin"/>
	<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.5"/>
	<classpathentry kind="src" path="/someProject"/>
</classpath>
"""

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'
"""
        //then
        assert classpath.entries.size() == 2
        assert classpath.containers.size() == 1
        assert classpath.containers == [jreContainerPath]
    }

    @Test
    @ToBeFixedForConfigurationCache
    void compileOnlyDependenciesAddedToClasspath() {
        // given
        mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'testCompileOnly', '1.0').publish()

        // when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.uri}" }
}

dependencies {
    compileOnly 'org.gradle.test:compileOnly:1.0'
    testCompileOnly 'org.gradle.test:testCompileOnly:1.0'
}
"""

        // then
        assert classpath.libs.size() == 2
        classpath.assertHasLibs('compileOnly-1.0.jar', 'testCompileOnly-1.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void compileOnlyDependenciesAreNotExported() {
        // given
        mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'compile', '1.0').publish()

        // when
        createDirs("a", "b")
        runEclipseTask "include 'a', 'b'", """
allprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'

    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

project(':a') {
    dependencies {
        compileOnly 'org.gradle.test:compileOnly:1.0'
    }
}

project(':b') {
    dependencies {
        implementation project(':a')
        implementation 'org.gradle.test:compile:1.0'
    }
}
"""

        // then
        def classpathA = classpath('a')
        def classpathB = classpath('b')
        assert classpathA.libs.size() == 1
        classpathA.assertHasLibs('compileOnly-1.0.jar')
        assert classpathB.libs.size() == 1
        assert classpathB.projects.collect { it.name } == ['a']
        classpathB.assertHasLibs('compile-1.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "test compile only dependencies mapped to classpath and not exported"() {
        // given
        mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'compile', '1.0').publish()

        // when
        createDirs("a", "b")
        runEclipseTask "include 'a', 'b'", """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'eclipse'

                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            project(':a') {
                dependencies {
                    testCompileOnly 'org.gradle.test:compileOnly:1.0'
                }
            }

            project(':b') {
                dependencies {
                    implementation project(':a')
                    implementation 'org.gradle.test:compile:1.0'
                }
            }
        """.stripIndent()

        // then
        def classpathA = classpath('a')
        def classpathB = classpath('b')
        assert classpathA.libs.size() == 1
        classpathA.assertHasLibs('compileOnly-1.0.jar')
        assert classpathB.libs.size() == 1
        assert classpathB.projects.collect { it.name } == ['a']
        classpathB.assertHasLibs('compile-1.0.jar')
    }

    /*
     * This is a test describing the current, not necessarily desired behavior. We really shouldn't
     * put duplicate dependencies on the classpath. The order will always be arbitrary and break one
     * use case or another.
     */
    @Test
    @ToBeFixedForConfigurationCache
    void "conflicting versions of the same library for compile and compile-only mapped to classpath"() {
        // given
        mavenRepo.module('org.gradle.test', 'conflictingDependency', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'conflictingDependency', '2.0').publish()

        // when
        createDirs("a", "b")
        runEclipseTask "include 'a', 'b'", """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'eclipse'

                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            project(':a') {
                dependencies {
                    implementation 'org.gradle.test:conflictingDependency:1.0'
                    compileOnly 'org.gradle.test:conflictingDependency:2.0'
                }
            }

            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """.stripIndent()

        // then
        def classpathA = classpath('a')
        def classpathB = classpath('b')
        assert classpathA.libs.size() == 2
        classpathA.assertHasLibs('conflictingDependency-2.0.jar', 'conflictingDependency-1.0.jar')
        assert classpathB.libs.size() == 1
        assert classpathB.projects.collect { it.name } == ['a']
        classpathB.assertHasLibs('conflictingDependency-1.0.jar')
    }

    /*
     * This is a test describing the current, not necessarily desired behavior. We really shouldn't
     * put duplicate dependencies on the classpath. The order will always be arbitrary and break one
     * use case or another.
     */
    @Test
    @ToBeFixedForConfigurationCache
    void "conflicting versions of the same library for runtime and compile-only mapped to classpath"() {
        // given
        mavenRepo.module('org.gradle.test', 'conflictingDependency', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'conflictingDependency', '2.0').publish()

        // when
        createDirs("a", "b")
        runEclipseTask "include 'a', 'b'", """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'eclipse'

                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            project(':a') {
                dependencies {
                    runtimeOnly 'org.gradle.test:conflictingDependency:1.0'
                    compileOnly 'org.gradle.test:conflictingDependency:2.0'
                }
            }

            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """.stripIndent()

        // then
        def classpathA = classpath('a')
        def classpathB = classpath('b')
        assert classpathA.libs.size() == 2
        classpathA.assertHasLibs('conflictingDependency-2.0.jar', 'conflictingDependency-1.0.jar')
        assert classpathB.libs.size() == 1
        assert classpathB.projects.collect { it.name } == ['a']
        classpathB.assertHasLibs('conflictingDependency-1.0.jar')
    }

    /*
     * This is a test describing the current, not necessarily desired behavior. We really shouldn't
     * put duplicate dependencies on the classpath. The order will always be arbitrary and break one
     * use case or another.
     */
    @Test
    @ToBeFixedForConfigurationCache
    void "conflicting versions of the same library for test-compile and testcompile-only mapped to classpath"() {
        // given
        mavenRepo.module('org.gradle.test', 'conflictingDependency', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'conflictingDependency', '2.0').publish()

        // when
        createDirs("a", "b")
        runEclipseTask "include 'a', 'b'", """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'eclipse'

                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            project(':a') {
                dependencies {
                    testImplementation 'org.gradle.test:conflictingDependency:1.0'
                    testCompileOnly 'org.gradle.test:conflictingDependency:2.0'
                }
            }

            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """.stripIndent()

        // then
        def classpathA = classpath('a')
        def classpathB = classpath('b')
        assert classpathA.libs.size() == 2
        classpathA.assertHasLibs('conflictingDependency-2.0.jar', 'conflictingDependency-1.0.jar')
        assert classpathB.libs.size() == 0
        assert classpathB.projects.collect { it.name } == ['a']
    }
}
