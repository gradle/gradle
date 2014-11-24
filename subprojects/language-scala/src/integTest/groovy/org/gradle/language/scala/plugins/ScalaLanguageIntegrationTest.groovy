/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.scala.plugins
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.scala.fixtures.TestScalaLibrary
import org.gradle.test.fixtures.archive.JarTestFixture

class ScalaLanguageIntegrationTest extends AbstractIntegrationSpec{

    def app = new TestScalaLibrary()

    def "can build binary with sources in conventional location"() {
        when:
        app.sources*.writeToDir(file("src/myLib/scala"))
        app.resources*.writeToDir(file("src/myLib/resources"))
        def expectedOutputs = app.expectedOutputs*.fullPath as String[]

        and:
        buildFile << """
    plugins {
        id 'jvm-component'
        id 'scala-lang'
    }
    model {
        components {
            myLib(JvmLibrarySpec)
        }
    }

    repositories {
        mavenCentral()
    }

"""
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":processMyLibJarMyLibResources", ":compileMyLibJarMyLibScala", ":createMyLibJar", ":myLibJar"

        and:
        file("build/classes/myLibJar").assertHasDescendants(expectedOutputs)
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(expectedOutputs)
    }

    private JarTestFixture jarFile(String s) {
        new JarTestFixture(file(s))
    }

}
