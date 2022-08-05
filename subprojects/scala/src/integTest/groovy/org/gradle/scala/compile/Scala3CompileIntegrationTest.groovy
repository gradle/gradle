/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.scala.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.junit.Rule

class Scala3CompileIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, temporaryFolder)

    def setup() {
        executer.withRepositoryMirrors()
    }

    def 'compile simple optional braces class'() {
        given:
        buildFile << """
plugins {
    id 'scala'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.scala-lang:scala3-library_3:3.0.1'
}

"""
        file('src/main/scala/org/test/Person.scala') << """
package org.test

class Person(name: String):
    def getName(): String = name

"""
        when:
        succeeds 'compileScala'

        then:
        file('build/classes/scala/main/org/test/Person.class').exists()
    }

    def 'fail in case of errors and print correct positions'() {
        given:
        buildFile << """
plugins {
    id 'scala'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.scala-lang:scala3-library_3:3.0.1'
}

"""
        file('src/main/scala/org/test/Person.scala') << """
package org.test

class Person(name: String):
    def getName(): String =
      1 + 23

"""
        expect:
        fails 'compileScala'
        result.assertHasErrorOutput("Person.scala:6:11: Found:    (23 : Int)")
    }

}
