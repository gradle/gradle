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
    void allowsConfiguringEclipseClasspath() {
        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

sourceSets.main.java.srcDirs.each { it.mkdirs() }
sourceSets.main.resources.srcDirs.each { it.mkdirs() }

configurations {
  someConfig
  someOtherConfig
}

dependencies {
  someConfig files('foo.txt', 'bar.txt', 'baz.txt')
  someOtherConfig files('baz.txt')
}

eclipse {

  pathVariables fooPathVariable: new File('.')

  classpath {
    sourceSets = []

    plusConfigurations += configurations.someConfig
    minusConfigurations += configurations.someOtherConfig

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

        content = getFile([:], '.classpath').text

        //then
        contains('foo.txt')
        contains('bar.txt')

        contains('fooPathVariable')
        contains('someFriendlyContainer', 'andYetAnotherContainer')

        contains('build-eclipse')
        contains('<message>be cool')
    }

    @Test
    void allowsConfiguringHooks() {
        //given
        def classpath = getClasspathFile([:])
        classpath << '''<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="output" path="bin"/>
	<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER" exported="true"/>
	<classpathentry kind="lib" path="/some/path/someDependency.jar" exported="true"/>
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
        assert it.entries.any { it.path.contains('someDependency.jar') }
        assert !it.entries.any { it.path.contains('newDependency.jar') }
      }
      whenMerged {
        hooks << 'whenMerged'
        assert it.entries.any { it.path.contains('newDependency.jar') }
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
    void createsLinkedResourcesForClasspathFoldersNotBeneathProjectDir() {
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

        assert !classpath.contains('path="../someGroovySrc"') : "external src folders are not supported by eclipse"
        assert classpath.contains('path="someGroovySrc"') : "external folder should be mapped to linked resource"

        def project = parseProjectFile(project: 'api')

        assert project.linkedResources.link.location.text().contains('someGroovySrc') : 'should contain linked resource for folder that is not beneath the project dir'
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
        def out = parseClasspathFile(print: true)
        def libPaths = out.classpathentry.findAll { it.@kind.text() == 'lib' }.collect { it.@path.text() }
        assert libPaths == ['build/generated/main', 'build/generated/test']
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

    protected def contains(String ... contents) {
        contents.each { assert content.contains(it)}
    }
}
