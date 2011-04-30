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
 * Author: Szczepan Faber
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

    classesOutputDir = file('build-eclipse')

    downloadSources = false
    downloadJavadoc = true
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

    protected def contains(String ... contents) {
        contents.each { assert content.contains(it)}
    }
}
