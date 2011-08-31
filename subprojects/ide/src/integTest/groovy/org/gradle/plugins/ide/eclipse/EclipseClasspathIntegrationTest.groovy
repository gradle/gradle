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

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

/**
 * @author Szczepan Faber
 */
class EclipseClasspathIntegrationTest extends AbstractEclipseIntegrationTest {

    @Rule
    public final TestResources testResources = new TestResources()

    String content

    @Test
    void "classpath contains library entries for external and file dependencies"() {
        //given
        def jar = mavenRepo.module('coolGroup', 'niceArtifact', '1.0').publishArtifact()
        def srcJar = mavenRepo.module('coolGroup', 'niceArtifact', '1.0', 'sources').publishArtifact()
        mavenRepo.module('coolGroup', 'niceArtifact', '1.0', 'javadoc').publishArtifact()

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.rootDir.toURI()}" }
}

dependencies {
    compile 'coolGroup:niceArtifact:1.0'
    compile files('lib/dep.jar')
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 2
        libraries[0].assertHasJar(jar)
        libraries[0].assertHasSource(srcJar)
        libraries[0].assertHasNoJavadoc()
        libraries[1].assertHasJar(file('lib/dep.jar'))
        libraries[1].assertHasNoSource()
        libraries[1].assertHasNoJavadoc()
    }

    @Test
    void "substitutes path variables into library paths"() {
        //given
        mavenRepo.module('coolGroup', 'niceArtifact', '1.0').publishArtifact()
        mavenRepo.module('coolGroup', 'niceArtifact', '1.0', 'sources').publishArtifact()
        mavenRepo.module('coolGroup', 'niceArtifact', '1.0', 'javadoc').publishArtifact()

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.rootDir.toURI()}" }
}

dependencies {
    compile 'coolGroup:niceArtifact:1.0'
    compile files('lib/dep.jar')
}

eclipse {
    pathVariables REPO_DIR: file('${mavenRepo.rootDir.toURI()}')
    pathVariables LIB_DIR: file('lib')
    classpath.downloadJavadoc = true
}
"""

        //then
        def libraries = classpath.vars
        assert libraries.size() == 2
        libraries[0].assertHasJar('REPO_DIR/coolGroup/niceArtifact/1.0/niceArtifact-1.0.jar')
        libraries[0].assertHasSource('REPO_DIR/coolGroup/niceArtifact/1.0/niceArtifact-1.0-sources.jar')
        libraries[0].assertHasJavadoc('REPO_DIR/coolGroup/niceArtifact/1.0/niceArtifact-1.0-javadoc.jar')
        libraries[1].assertHasJar('LIB_DIR/dep.jar')
    }

    @Test
    void "can customise the classpath model"() {
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

    plusConfigurations += configurations.someConfig

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
        content = getFile([print: true], '.classpath').text

        //then
        contains('foo.txt')

        contains('fooPathVariable')
        contains('someFriendlyContainer', 'andYetAnotherContainer')

        contains('build-eclipse')
        contains('<message>be cool')
    }

    @Test
    @Issue("GRADLE-1487")
    void "handles plus minus configurations for self resolving deps"() {
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
    plusConfigurations += configurations.someConfig
    minusConfigurations += configurations.someOtherConfig
}
"""
        content = getFile([print: true], '.classpath').text

        //then
        contains 'foo.txt', 'bar.txt'
        assert !content.contains('unwanted.txt')

        //then
        def libraries = classpath.libs
        assert libraries.size() == 2
        libraries[0].assertHasJar(file("foo.txt"))
        libraries[1].assertHasJar(file("bar.txt"))
    }

    @Test
    void "handles plus minus configurations for project deps"() {
        //when
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
    plusConfigurations += configurations.someConfig
    minusConfigurations += configurations.someOtherConfig
}
"""
        content = getFile([print: true], '.classpath').text

        //then
        contains 'foo', 'bar'
        assert !content.contains('unwanted')
    }

    @Test
    void "handles plus minus configurations for external deps"() {
        //given
        def jar = mavenRepo.module('coolGroup', 'coolArtifact', '1.0').dependsOn('coolGroup', 'unwantedArtifact', '1.0').publishArtifact()
        mavenRepo.module('coolGroup', 'unwantedArtifact', '1.0').publishArtifact()

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

configurations {
  someConfig
  someOtherConfig
}

repositories {
    maven { url "${mavenRepo.rootDir.toURI()}" }
}

dependencies {
  someConfig 'coolGroup:coolArtifact:1.0'
  someOtherConfig 'coolGroup:unwantedArtifact:1.0'
}

eclipse.classpath {
    plusConfigurations += configurations.someConfig
    minusConfigurations += configurations.someOtherConfig
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 1
        libraries[0].assertHasJar(jar)
    }

    @Test
    void "can toggle javadoc and sources on"() {
        //given
        def jar = mavenRepo.module('coolGroup', 'niceArtifact', '1.0').publishArtifact()
        def srcJar = mavenRepo.module('coolGroup', 'niceArtifact', '1.0', 'sources').publishArtifact()
        def javadocJar = mavenRepo.module('coolGroup', 'niceArtifact', '1.0', 'javadoc').publishArtifact()

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.rootDir.toURI()}" }
}

dependencies {
    compile 'coolGroup:niceArtifact:1.0'
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
    void "can toggle javadoc and sources off"() {
        //given
        def jar = mavenRepo.module('coolGroup', 'niceArtifact', '1.0').publishArtifact()
        mavenRepo.module('coolGroup', 'niceArtifact', '1.0', 'sources').publishArtifact()
        mavenRepo.module('coolGroup', 'niceArtifact', '1.0', 'javadoc').publishArtifact()

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    maven { url "${mavenRepo.rootDir.toURI()}" }
}

dependencies {
    compile 'coolGroup:niceArtifact:1.0'
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
    void "removes dependencies from existing classpath file when merging"() {
        //given
        getClasspathFile() << '''<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="output" path="bin"/>
	<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER" exported="true"/>
	<classpathentry kind="lib" path="/some/path/someDependency.jar" exported="true"/>
	<classpathentry kind="var" path="SOME_VAR/someVarDependency.jar" exported="true"/>
	<classpathentry kind="src" path="/someProject" exported="true"/>
</classpath>
'''

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

dependencies {
  compile files('newDependency.jar')
}
"""

        //then
        assert classpath.entries.size() == 3
        def libraries = classpath.libs
        assert libraries.size() == 1
        libraries[0].assertHasJar(file('newDependency.jar'))
    }

    @Test
    void "can access xml model before and after generation"() {
        //given
        def classpath = getClasspathFile([:])
        classpath << '''<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="output" path="bin"/>
	<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER" exported="true"/>
	<classpathentry kind="lib" path="/some/path/someDependency.jar" exported="true"/>
	<classpathentry kind="var" path="SOME_VAR/someVarDependency.jar" exported="true"/>
</classpath>
'''

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

def hooks = []

dependencies {
  compile files('newDependency.jar')
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
    void "creates linked resources for source directories which are not under the project directory"() {
        file('someGroovySrc').mkdirs()

        def settingsFile = file('settings.gradle')
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
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks('eclipse').run()

        //then
        def classpath = getClasspathFile(project: 'api').text

        assert !classpath.contains('path="../someGroovySrc"'): "external src folders are not supported by eclipse"
        assert classpath.contains('path="someGroovySrc"'): "external folder should be mapped to linked resource"

        def project = parseProjectFile(project: 'api')

        assert project.linkedResources.link.location.text().contains('someGroovySrc'): 'should contain linked resource for folder that is not beneath the project dir'
    }

    @Issue("GRADLE-1402")
    @Test
    void "should put sourceSet's output dir on classpath"() {
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
    void "the 'buildBy' task be executed"() {
        //when
        def result = runEclipseTask('''
apply plugin: "java"
apply plugin: "eclipse"

sourceSets.main.output.dir "$buildDir/generated/main", buildBy: 'generateForMain'
sourceSets.test.output.dir "$buildDir/generated/test", buildBy: 'generateForTest'

task generateForMain << {}
task generateForTest << {}
''')
        //then
        result.assertTasksExecuted(':generateForMain', ':generateForTest', ':eclipseClasspath', ':eclipseJdt', ':eclipseProject', ':eclipse')
    }

    @Test
    @Issue("GRADLE-1613")
    void "should allow setting non-exported configurations"() {
        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

configurations {
  provided
}

dependencies {
  compile  files('compileDependency.jar')
  provided files('providedDependency.jar')
}

eclipse {
  classpath {
    plusConfigurations += configurations.provided
    noExportConfigurations += configurations.provided
  }
}
"""

        //then
        def libraries = classpath.libs
        assert libraries.size() == 2
        libraries[0].assertHasJar(file('compileDependency.jar'))
        libraries[0].assertExported()
        libraries[1].assertHasJar(file('providedDependency.jar'))
        libraries[1].assertNotExported()
    }

    protected def contains(String... wanted) {
        wanted.each { assert content.contains(it)}
    }
}
