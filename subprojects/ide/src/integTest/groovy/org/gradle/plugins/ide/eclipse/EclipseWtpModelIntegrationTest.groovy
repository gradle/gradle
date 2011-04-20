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
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Author: Szczepan Faber, created at: 4/19/11
 */
class EclipseWtpModelIntegrationTest extends AbstractEclipseIntegrationTest {

    @Rule
    public final TestResources testResources = new TestResources()

    String component

    @Test
    void allowsConfiguringEclipseProject() {
        //given
        file('someExtraSourceDir').mkdirs()

        def repoDir = file("repo")
        publishArtifact(repoDir, "gradle", "foo")
        publishArtifact(repoDir, "gradle", "bar")
        publishArtifact(repoDir, "gradle", "baz")


        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'war'
apply plugin: 'eclipse'

configurations {
  configOne
  configTwo
}

repositories {
  mavenRepo urls: "${repoDir.toURI()}"
}

dependencies {
  configOne 'gradle:foo:1.0', 'gradle:bar:1.0', 'gradle:baz:1.0'
  configTwo 'gradle:baz:1.0'
}

eclipse {

  pathVariables 'userHomeVariable' : file(System.properties['user.home'])

  wtp {
    component {
      contextPath = 'killerApp'

      sourceDirs += file('someExtraSourceDir')

      plusConfigurations += configurations.configOne
      minusConfigurations += configurations.configTwo

      deployName = 'someBetterDeployName'

      resource sourcePath: './src/foo/bar', deployPath: './deploy/foo/bar'

      property name: 'wbPropertyOne', value: 'New York!'
    }
    facet {
      facet name: 'gradleFacet', version: '1.333'
    }
  }
}
        """

        component = getFile([:], '.settings/org.eclipse.wst.common.component').text
        def facet = getFile([:], '.settings/org.eclipse.wst.common.project.facet.core.xml').text
        println facet //TODO SF after completing the refactoring, get rid of the printlns

        //then component:
        contains('someExtraSourceDir')

        contains('foo-1.0.jar', 'bar-1.0.jar')
        assert !component.contains('baz-1.0.jar')

        contains('someBetterDeployName')

        //contains('userHomeVariable') //TODO SF don't know how to test it at the moment

        contains('./src/foo/bar', './deploy/foo/bar')
        contains('wbPropertyOne', 'New York!')

        contains('killerApp')

        assert facet.contains('gradleFacet')
        assert facet.contains('1.333')
    }

    @Ignore("GRADLE-1487")
    @Test
    void allowsFileDependencies() {
        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'war'
apply plugin: 'eclipse'

configurations {
  configOne
  configTwo
}

dependencies {
  configOne files('foo.txt', 'bar.txt', 'baz.txt')
  configTwo files('baz.txt')
}

eclipse {
  wtp {
    plusConfigurations += configurations.configOne
    minusConfigurations += configurations.configTwo
  }
}
        """

        def component = getFile([:], '.settings/org.eclipse.wst.common.component').text
        println component

        //then
    }

    @Test
    void createsTasksOnDependantUponProjectsEvenIfTheyDontHaveWarPlugin() {
        //given
        def settings = file('settings.gradle')
        settings << "include 'impl', 'contrib'"

        def build = file('build.gradle')
        build << """
project(':impl') {
  apply plugin: 'java'
  apply plugin: 'war'
  apply plugin: 'eclipse'

  dependencies { compile project(':contrib') }
}

project(':contrib') {
  apply plugin: 'java'
  apply plugin: 'eclipse'
}
"""
        //when
        executer.usingSettingsFile(settings).usingBuildScript(build).withTasks('eclipse').run()

        //then
        getComponentFile(project: 'impl')
        getFacetFile(project: 'impl')

        getComponentFile(project: 'contrib')
        getFacetFile(project: 'contrib')
    }

    protected def contains(String ... contents) {
        contents.each { assert component.contains(it)}
    }
}
