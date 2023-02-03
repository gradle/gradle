/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.junit.Rule

class ScalaProjectIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, testDirectoryProvider)

    def "handles java source only"() {
        file("src/main/java/somepackage/SomeClass.java") << "public class SomeClass { }"
        buildFile << """
            apply plugin: 'scala'
        """
        settingsFile << """
            rootProject.name = 'javaOnly'
            dependencyResolutionManagement {
                ${mavenCentralRepository()}
            }
        """
        expect:
        succeeds "build"
        file("build/libs/javaOnly.jar").assertExists()
    }

    def "supports central repository declaration"() {
        given:
        buildFile << """
plugins {
    id 'scala'
}
dependencies {
    implementation 'org.scala-lang:scala-library:2.11.12'
}
"""
        settingsFile << """
rootProject.name = 'scalaCompilation'
dependencyResolutionManagement {
    ${mavenCentralRepository()}
}
"""
        and:
        file('src/main/scala/Test.scala') << """
class Test { }
"""
        when:
        succeeds 'compileScala'

        then:
        executedAndNotSkipped(':compileScala')
    }
}
