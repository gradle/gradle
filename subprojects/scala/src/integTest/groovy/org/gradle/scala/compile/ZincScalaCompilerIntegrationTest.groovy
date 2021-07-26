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
package org.gradle.scala.compile

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.junit.Assume
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators
import static org.hamcrest.core.IsNull.notNullValue

@TargetCoverage({ ScalaCoverage.DEFAULT })
class ZincScalaCompilerIntegrationTest extends BasicZincScalaCompilerIntegrationTest {

    def "respects fork options settings and ignores executable"() {
        def differentJvm = AvailableJavaHomes.differentJdk
        Assume.assumeThat(differentJvm, notNullValue())
        def differentJavaExecutablePath = normaliseFileSeparators(differentJvm.javaExecutable.absolutePath)

        file("build.gradle") << """
            import org.gradle.workers.internal.WorkerDaemonClientsManager
            import org.gradle.internal.jvm.Jvm

            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.scala-lang:scala-library:2.11.12'
            }

            tasks.withType(ScalaCompile) {
                options.forkOptions.executable = "${differentJavaExecutablePath}"
                options.forkOptions.memoryInitialSize = "128m"
                options.forkOptions.memoryMaximumSize = "256m"
                options.forkOptions.jvmArgs = ["-Dfoo=bar"]

                doLast {
                    assert services.get(WorkerDaemonClientsManager).idleClients.find {
                        new File(it.forkOptions.javaForkOptions.executable).canonicalPath == Jvm.current().javaExecutable.canonicalPath &&
                        it.forkOptions.javaForkOptions.minHeapSize == "128m" &&
                        it.forkOptions.javaForkOptions.maxHeapSize == "256m" &&
                        it.forkOptions.javaForkOptions.systemProperties['foo'] == "bar"
                    }
                }
            }
        """

        file("src/main/scala/Person.java") << "public interface Person { String getName(); }"

        file("src/main/scala/DefaultPerson.scala") << """class DefaultPerson(name: String) extends Person {
            def getName(): String = name
        }"""

        expect:
        succeeds("compileScala")

    }

    def compilesScalaCodeIncrementally() {
        setup:
        def person = scalaClassFile("Person.class")
        def house = scalaClassFile("House.class")
        def other = scalaClassFile("Other.class")
        run("compileScala")

        when:
        file("src/main/scala/Person.scala").delete()
        file("src/main/scala/Person.scala") << "class Person"
        args("-PscalaVersion=$version") // each run clears args (argh!)
        run("compileScala")

        then:
        classHash(person) != old(classHash(person))
        classHash(house) != old(classHash(house))
        other.lastModified() == old(other.lastModified())
    }

    def compilesJavaCodeIncrementally() {
        setup:
        def person = scalaClassFile("Person.class")
        def house = scalaClassFile("House.class")
        def other = scalaClassFile("Other.class")
        run("compileScala")

        when:
        file("src/main/scala/Person.java").delete()
        file("src/main/scala/Person.java") << "public class Person {}"
        args("-PscalaVersion=$version") // each run clears args (argh!)
        run("compileScala")

        then:
        person.lastModified() != old(person.lastModified())
        house.lastModified() != old(house.lastModified())
        other.lastModified() == old(other.lastModified())
    }

    def compilesIncrementallyAcrossProjectBoundaries() {
        setup:
        def person = file("prj1/build/classes/scala/main/Person.class")
        def house = file("prj2/build/classes/scala/main/House.class")
        def other = file("prj2/build/classes/scala/main/Other.class")
        args("-PscalaVersion=$version")
        run("compileScala")

        when:
        file("prj1/src/main/scala/Person.scala").delete()
        file("prj1/src/main/scala/Person.scala") << "class Person"
        args("-PscalaVersion=$version") // each run clears args (argh!)
        run("compileScala")

        then:
        classHash(person) != old(classHash(person))
        classHash(house) != old(classHash(house))
        other.lastModified() == old(other.lastModified())

    }

    def compilesAllScalaCodeWhenForced() {
        setup:
        def person = scalaClassFile("Person.class")
        def house = scalaClassFile("House.class")
        def other = scalaClassFile("Other.class")
        run("compileScala")

        when:
        file("src/main/scala/Person.scala").delete()
        file("src/main/scala/Person.scala") << "class Person"
        args("-PscalaVersion=$version") // each run clears args (argh!)
        run("compileScala")

        then:
        classHash(person) != old(classHash(person))
        house.lastModified() != old(house.lastModified())
        other.lastModified() != old(other.lastModified())
    }

    @Issue("gradle/gradle#13535")
    def doNotPropagateWorkerClasspathToCompilationClasspath() {
        given:
        // scala 2.12 is used because for 2.13 this particular case is ok
        file("build.gradle") << """
            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.scala-lang:scala-library:2.12.11'
            }
        """

        file("src/main/scala/ScalaXml.scala") << """
            import scala.xml.Text

            object ScalaXml {
              def main(args: Array[String]): Unit = {
                val text = new Text("test")
                println(text)
              }
            }"""

        expect:
        fails("compileScala")
        result.assertHasErrorOutput("object xml is not a member of package scala")
    }

}
