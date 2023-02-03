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
import spock.lang.Issue

class ForceScalaCompileIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, temporaryFolder)

    def setup() {
        executer.withRepositoryMirrors()
    }

    @Issue("gradle/gradle#13224")
    def 'disabling incremental compilation does not produce an analysis file'() {
        given:
        buildFile << """
plugins {
    id 'scala'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.scala-lang:scala-library:2.12.11'
}

tasks.withType(ScalaCompile).configureEach {
    scalaCompileOptions.force = true
}

"""
        file('src/main/scala/org/test/Person.scala') << """
package org.test

class Person(name: String) {
    def getName(): String = name
}
"""
        when:
        succeeds 'compileScala'

        then:
        !file('build/tmp/scala/compilerAnalysis/compileScala.analysis').exists()
        file('build/tmp/scala/compilerAnalysis/compileScala.mapping').exists()
    }

    @Issue("gradle/gradle#13224")
    def 'changing one file only produces valid compilation output'() {
        given:
        buildFile << """
plugins {
    id 'scala'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.scala-lang:scala-library:2.12.11'
}

tasks.withType(ScalaCompile).configureEach {
    scalaCompileOptions.force = true
}

"""
        file('src/main/scala/org/test/Person.scala') << """
package org.test

class Person(name: String) {
    def getName(): String = name
}
"""
        file('src/main/scala/org/test/Other.scala') << """
package org.test

class Other(thing: String) {
    def getThing(): String = thing
}
"""
        when:
        succeeds 'compileScala'

        then:
        file('build/classes/scala/main/org/test/Person.class').exists()
        file('build/classes/scala/main/org/test/Other.class').exists()

        when:
        file('src/main/scala/org/test/Other.scala').text = """
package org.test

class Other(name: String, thing: String) {
    def getName(): String = name
    def getThing(): String = thing
}
"""
        succeeds 'compileScala'

        then:
        file('build/classes/scala/main/org/test/Person.class').exists()
        file('build/classes/scala/main/org/test/Other.class').exists()
    }

}
