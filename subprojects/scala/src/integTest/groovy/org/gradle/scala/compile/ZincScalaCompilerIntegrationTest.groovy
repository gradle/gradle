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

import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

@TargetCoverage({ScalaCoverage.DEFAULT})
class ZincScalaCompilerIntegrationTest extends BasicScalaCompilerIntegrationTest {
    @Rule TestResources testResources = new TestResources(temporaryFolder)

    String logStatement() {
        "Compiling with Zinc Scala compiler"
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
        args("-i", "-PscalaVersion=$version") // each run clears args (argh!)
        executer.expectDeprecationWarning() // each run clears args (argh!)
        run("compileScala")

        then:
        person.exists()
        house.exists()
        other.exists()
        person.lastModified() != old(person.lastModified())
        house.lastModified() != old(house.lastModified())
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
        args("-i", "-PscalaVersion=$version") // each run clears args (argh!)
        executer.expectDeprecationWarning() // each run clears args (argh!)
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
        run("compileScala")

        when:
        file("prj1/src/main/scala/Person.scala").delete()
        file("prj1/src/main/scala/Person.scala") << "class Person"
        args("-i", "-PscalaVersion=$version") // each run clears args (argh!)
        executer.expectDeprecationWarning() // each run clears args (argh!)
        run("compileScala")

        then:
        person.lastModified() != old(person.lastModified())
        house.lastModified() != old(house.lastModified())
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
        args("-i", "-PscalaVersion=$version") // each run clears args (argh!)
        executer.expectDeprecationWarning() // each run clears args (argh!)
        run("compileScala")

        then:
        person.lastModified() != old(person.lastModified())
        house.lastModified() != old(house.lastModified())
        other.lastModified() != old(other.lastModified())
    }
}
