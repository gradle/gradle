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
  classpath {
    sourceSets = []

    plusConfigurations += configurations.someConfig
    minusConfigurations += configurations.someOtherConfig

    pathVariables fooPathVariable: new File('.')

    containers 'someFriendlyContainer', 'andYetAnotherContainer'

    classesOutputDir = file('build-eclipse')
  }
}
"""

        content = getFile([:], '.classpath').text
        println content

        //then
        contains('foo.txt')
        contains('bar.txt')
        //assert !content.contains('baz.txt') //TODO SF - why it does not work?

        contains('fooPathVariable')
        contains('someFriendlyContainer', 'andYetAnotherContainer')

        contains('build-eclipse')
    }

    protected def contains(String ... contents) {
        contents.each { assert content.contains(it)}
    }
}
