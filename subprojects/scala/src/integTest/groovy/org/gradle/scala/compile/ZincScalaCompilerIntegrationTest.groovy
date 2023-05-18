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

import static org.gradle.scala.ScalaCompilationFixture.scalaDependency
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
                implementation "${scalaDependency(version.toString())}"
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

    def "compiles Scala code incrementally"() {
        file("src/main/scala/Person.scala") << """class Person(val name: String = "foo", val age: Int = 1)"""
        file("src/main/scala/House.scala") << """class House(val owner: Person = new Person())"""
        file("src/main/scala/Other.scala") << """class Other"""
        file("src/main/scala/Other2.scala") << """class Other2"""

        def person = scalaClassFile("Person.class")
        def house = scalaClassFile("House.class")
        def other = scalaClassFile("Other.class")
        // We need an additional file since if >50% of files is changed everything gets recompiled
        def other2 = scalaClassFile("Other2.class")

        when:
        run("compileScala")

        then:
        executedAndNotSkipped(":compileScala")

        when:
        file("src/main/scala/Person.scala").delete()
        file("src/main/scala/Person.scala") << "class Person"
        args("-PscalaVersion=$version") // each run clears args (argh!)
        run("compileScala")

        then:
        executedAndNotSkipped(":compileScala")
        classHash(person) != old(classHash(person))
        classHash(house) != old(classHash(house))
        classHash(other) == old(classHash(other))
        other.lastModified() == old(other.lastModified())
        other2.lastModified() == old(other2.lastModified())
    }

    def "compiles Java code incrementally"() {
        file("src/main/scala/Person.java") << """
            public class Person {
                private final String name;
                public Person(String name) { this.name = name; }
                public String getName() { return name; }
            }
        """
        file("src/main/scala/House.java") << """
            public class House {
                private final Person owner;
                public House(Person owner) { this.owner = owner; }
                public Person getOwner() { return owner; }
            }
        """
        file("src/main/scala/Other.java") << """public class Other {}"""

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

    def "compiles Scala incrementally across project boundaries"() {
        file("settings.gradle") << """include 'a', 'b'"""
        // overwrite the build file from setup
        file("build.gradle").text = """
            subprojects {
                apply plugin: 'scala'
                ${mavenCentralRepository()}
                dependencies {
                    implementation "${scalaDependency(version.toString())}"
                }
            }
            project(":b") {
                dependencies {
                    implementation project(":a")
                }
            }
        """
        file("a/src/main/scala/Person.scala") << """class Person(val name: String = "foo", val age: Int = 1)"""
        file("b/src/main/scala/House.scala") << """class House(val owner: Person = new Person())"""
        file("b/src/main/scala/Other.scala") << """class Other"""

        def person = file("a/build/classes/scala/main/Person.class")
        def house = file("b/build/classes/scala/main/House.class")
        def other = file("b/build/classes/scala/main/Other.class")
        args("-PscalaVersion=$version")
        run("compileScala")

        when:
        file("a/src/main/scala/Person.scala").delete()
        file("a/src/main/scala/Person.scala") << "class Person"
        args("-PscalaVersion=$version") // each run clears args (argh!)
        run("compileScala")

        then:
        classHash(person) != old(classHash(person))
        classHash(house) != old(classHash(house))
        other.lastModified() == old(other.lastModified())
    }

    def "recompiles all Scala code when forced"() {
        file("src/main/scala/Person.scala") << """class Person(val name: String = "foo", val age: Int = 1)"""
        file("src/main/scala/House.scala") << """class House(val owner: Person = new Person())"""
        file("src/main/scala/Other.scala") << """class Other"""

        file("build.gradle") << """
            tasks.withType(ScalaCompile) {
                scalaCompileOptions.with {
                    force = true
                }
            }
        """

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

    @Issue("https://github.com/gradle/gradle/issues/13535")
    def doNotPropagateWorkerClasspathToCompilationClasspath() {
        // scala 2.12 is used because for 2.13 this particular case is ok
        Assume.assumeTrue(versionNumber.major == 2 && versionNumber.minor == 12)

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
