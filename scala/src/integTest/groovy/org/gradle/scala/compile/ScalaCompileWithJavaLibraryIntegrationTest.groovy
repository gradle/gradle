/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.junit.Rule

class ScalaCompileWithJavaLibraryIntegrationTest extends AbstractIntegrationSpec {

    @Rule TestResources resources = new TestResources(temporaryFolder)
    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, temporaryFolder)

    def setup() {
        executer.withRepositoryMirrors()
    }

    def javaLibraryCanDependOnScalaLibraryProject() {
        when:
        run ":compileJava"

        then:
        executedAndNotSkipped(":lib:compileScala")
        notExecuted(":lib:processResources", ":lib:jar")
    }

    def "scala and java source directory compilation order can be reversed (task configuration #configurationStyle)"() {
        given:
        file("src/main/scala/Scala.scala") << "class Scala { }"
        file("src/main/java/Java.java") << "public class Java { Scala scala = new Scala(); }"
        buildFile << """
            plugins {
                id 'scala'
            }
            $setup
            tasks.named('compileScala') {
                classpath = sourceSets.main.compileClasspath
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation("org.scala-lang:scala-library:2.11.12")
            }
        """

        when:
        succeeds 'compileJava'

        then:
        result.assertTasksExecutedInOrder(':compileScala', ':compileJava')

        where:
        configurationStyle | setup
        'lazy'             | "tasks.named('compileJava') { classpath += files(sourceSets.main.scala.classesDirectory) }"
        'eager'            | "compileJava { classpath += files(sourceSets.main.scala.classesDirectory) }"
    }

    def "scala and java source directory compilation order can be reversed for a custom source set"() {
        given:
        file("src/mySources/scala/Scala.scala") << "class Scala { }"
        file("src/mySources/java/Java.java") << "public class Java { Scala scala = new Scala(); }"
        buildFile << """
            plugins {
                id 'scala'
            }
            sourceSets {
                mySources
            }

            tasks.named('compileMySourcesJava') {
                classpath += files(sourceSets.mySources.scala.classesDirectory)
            }
            tasks.named('compileMySourcesScala') {
                classpath = sourceSets.mySources.compileClasspath
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                mySourcesImplementation("org.scala-lang:scala-library:2.11.12")
            }
        """

        when:
        succeeds 'compileMySourcesJava'

        then:
        result.assertTasksExecutedInOrder(':compileMySourcesScala', ':compileMySourcesJava')
    }

}
