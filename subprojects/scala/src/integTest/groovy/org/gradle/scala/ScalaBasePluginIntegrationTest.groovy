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

import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.ScalaCoverage
import org.junit.Rule

import static org.hamcrest.CoreMatchers.startsWith

@TargetCoverage({ScalaCoverage.DEFAULT})
class ScalaBasePluginIntegrationTest extends MultiVersionIntegrationSpec {
    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, temporaryFolder)

    def "defaults scalaClasspath to inferred Scala compiler dependency"() {
        file("build.gradle") << """
        apply plugin: "scala-base"

        sourceSets {
           custom
        }

        ${mavenCentralRepository()}

        dependencies {
           customImplementation "org.scala-lang:scala-library:$version"
        }

        task scaladoc(type: ScalaDoc) {
           classpath = sourceSets.custom.runtimeClasspath
        }

        task verify {
            doLast {
                assert compileCustomScala.scalaClasspath.files.any { it.name == "scala-compiler-${version}.jar" }
                assert scaladoc.scalaClasspath.files.any { it.name == "scala-compiler-${version}.jar" }
            }
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

${mavenCentralRepository()}

dependencies {
    customImplementation "org.scala-lang:scala-library:$version"
}

task scaladoc(type: ScalaDoc) {
    classpath = sourceSets.custom.runtimeClasspath
}

task verify {
    doLast {
        assert configurations.customCompile.state.toString() == "UNRESOLVED"
        assert configurations.customRuntime.state.toString() == "UNRESOLVED"
    }
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

            ${mavenCentralRepository()}

            dependencies {
                implementation "com.google.guava:guava:11.0.2"
            }
        """

        file("src/main/scala/Thing.scala") << """
            class Thing
        """

        when:
        fails "compileScala"

        then:
        failure.assertThatCause(startsWith("Cannot infer Scala class path because no Scala library Jar was found."))
    }

}
