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
package org.gradle.scala.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.junit.Rule
import spock.lang.Issue

class IncrementalScalaCompileIntegrationTest extends AbstractIntegrationSpec {

    @Rule TestResources resources = new TestResources(temporaryFolder)
    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, temporaryFolder)

    def setup() {
        executer.withRepositoryMirrors()
    }

    def recompilesSourceWhenPropertiesChange() {
        expect:
        run('compileScala').assertTasksSkipped(':compileJava')

        when:
        file('build.gradle').text += '''
            compileScala.options.debug = false
        '''
        then:
        run('compileScala').assertTasksSkipped(':compileJava')
        run('compileScala').assertTasksSkipped(':compileJava', ':compileScala')
    }

    def recompilesDependentClasses() {
        given:
        run("classes")

        when: // Update interface, compile should fail
        file('src/main/scala/IPerson.scala').assertIsFile().copyFrom(file('NewIPerson.scala'))

        then:
        runAndFail("classes").assertHasDescription("Execution failed for task ':compileScala'.")
    }

    @Issue("gradle/gradle#13392")
    def restoresClassesOnCompilationFailure() {
        given:
        run("classes")
        def iperson = scalaClassFile("IPerson.class")
        def person = scalaClassFile("Person.class")

        when: // Update interface, compile should fail
        file('src/main/scala/IPerson.scala').assertIsFile().copyFrom(file('NewIPerson.scala'))

        runAndFail("classes").assertHasDescription("Execution failed for task ':compileScala'.")

        then:
        // both files must exist (same content as in previous compilation)
        // this is needed for incremental compilation, outputs must be kept in sync with analysis file
        iperson.assertIsFile()
        person.assertIsFile()
    }

    @Issue("GRADLE-2548")
    def "recompiles Scala when Java changes"() {
        file("build.gradle") << """
            plugins {
                id("scala")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.scala-lang:scala-library:2.11.12'
            }
        """

        file("src/main/java/Person.java") << "public interface Person { String getName(); }"

        file("src/main/scala/DefaultPerson.scala") << """class DefaultPerson(name: String) extends Person {
            def getName(): String = name
            }
        """
        when:
        run('classes') //makes everything up-to-date

        //change the java interface
        file("src/main/java/Person.java").text = "public interface Person { String fooBar(); }"

        then:
        //the build should fail because the interface the scala class needs has changed
        runAndFail("classes").assertHasDescription("Execution failed for task ':compileScala'.")
    }

    @Issue("gradle/gradle#8421")
    def "incremental compiler detects change in package"() {
        settingsFile << """
            include 'lib'
        """
        [buildFile, file('lib/build.gradle')].each {
            it << """
                plugins {
                    id 'scala'
                }

                    ${mavenCentralRepository()}

                dependencies {
                   implementation 'org.scala-lang:scala-library:2.11.12'
                }
            """
        }

        buildFile << """
            dependencies {
                implementation project(":lib")
            }
        """

        file("src/main/scala/Hello.scala") << """import pkg1.Other

            class Hello extends Other
        """

        file("lib/src/main/scala/pkg1/Other.scala") << """
            package pkg1

            class Other
        """

        when:
        run ':build'

        then:
        executedAndNotSkipped(":lib:compileScala", ":compileScala")

        when:
        file("lib/src/main/scala/pkg1/Other.scala").delete()
        file("lib/src/main/scala/pkg2/Other.scala") << """
            package pkg2

            class Other
        """
        fails ':build'

        then:
        executedAndNotSkipped(":lib:compileScala", ":compileScala")
        failure.assertHasCause("Compilation failed")
    }

    def "incremental compiler gracefully handles classes being deleted"() {
        settingsFile << """
            dependencyResolutionManagement {
                ${mavenCentralRepository()}
            }
        """

        buildFile << """
            plugins {
                id 'scala'
                id 'application'
            }
            application {
                mainClass = "HelloWorld"
            }
            dependencies {
               implementation 'org.scala-lang:scala-library:2.11.12'
            }
        """

        file("src/main/scala/Hello.scala") << """
            object HelloWorld {
              def main(args: Array[String]): Unit = {
                println("Hello world")
              }
            }
        """

        when:
        succeeds "run"
        then:
        outputContains("Hello world")

        when:
        succeeds "cleanCompileScala", "run"
        then:
        outputContains("Hello world")
    }

}
