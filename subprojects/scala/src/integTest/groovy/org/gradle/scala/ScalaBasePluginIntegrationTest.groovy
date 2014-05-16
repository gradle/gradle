/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.scala

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.Matchers.startsWith

class ScalaBasePluginIntegrationTest extends AbstractIntegrationSpec {
    def "defaults scalaClasspath to inferred Scala compiler dependency"() {
        file("build.gradle") << """
apply plugin: "scala-base"

sourceSets {
    custom
}

repositories {
    mavenCentral()
}

dependencies {
    customCompile "org.scala-lang:scala-library:2.10.1"
}

task scaladoc(type: ScalaDoc) {
    classpath = sourceSets.custom.runtimeClasspath
}

task verify << {
    assert compileCustomScala.scalaClasspath.files.any { it.name == "scala-compiler-2.10.1.jar" }
    assert scalaCustomConsole.classpath.files.any { it.name == "scala-compiler-2.10.1.jar" }
    assert scaladoc.scalaClasspath.files.any { it.name == "scala-compiler-2.10.1.jar" }
}
"""

        expect:
        succeeds("verify")
    }

    def "only resolves source class path feeding into inferred Scala class path if/when the latter is actually used (but not during autowiring)"() {
        file("build.gradle") << """
apply plugin: "scala-base"

sourceSets {
    custom
}

repositories {
    mavenCentral()
}

dependencies {
    customCompile "org.scala-lang:scala-library:2.10.1"
}

task scaladoc(type: ScalaDoc) {
    classpath = sourceSets.custom.runtimeClasspath
}

task verify << {
    assert configurations.customCompile.state.toString() == "UNRESOLVED"
    assert configurations.customRuntime.state.toString() == "UNRESOLVED"
}
        """

        expect:
        succeeds("verify")
    }

    def "not specifying a scala runtime produces decent error message"() {
        given:
        buildFile << """
            apply plugin: "scala-base"

            sourceSets {
                main {}
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "com.google.guava:guava:11.0.2"
            }
        """

        file("src/main/scala/Thing.scala") << """
            class Thing
        """

        when:
        fails "compileScala"

        then:
        failure.assertThatDescription(startsWith("Cannot infer Scala class path because no Scala library Jar was found."))
    }

}