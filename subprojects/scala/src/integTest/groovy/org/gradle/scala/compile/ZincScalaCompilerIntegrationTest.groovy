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
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.ClassFile
import org.gradle.util.internal.VersionNumber
import org.junit.Assume
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition
import static org.gradle.util.internal.TextUtil.escapeString
import static org.gradle.util.internal.TextUtil.normaliseFileSeparators
import static org.hamcrest.core.IsNull.notNullValue

@TargetCoverage({ ScalaCoverage.DEFAULT })
class ZincScalaCompilerIntegrationTest extends MultiVersionIntegrationSpec {
    @Rule
    TestResources testResources = new TestResources(temporaryFolder)

    def setup() {
        args("-PscalaVersion=$version")
        buildFile << buildScript()
        executer.withRepositoryMirrors()
    }

    def compileGoodCode() {
        given:
        goodCode()

        expect:
        succeeds("compileScala")
        scalaClassFile("compile/test/Person.class").exists()
    }

    def compileBadCode() {
        given:
        badCode()

        expect:
        fails("compileScala")
        result.assertHasErrorOutput("type mismatch")
        scalaClassFile("").assertHasDescendants()
    }

    def useCompilerPluginIfDefined() {
        given:
        file("build.gradle") << """
            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.scala-lang:scala-library:2.13.1'
                scalaCompilerPlugins "org.typelevel:kind-projector_2.13.1:0.11.0"
            }
        """

        file("src/main/scala/KingProjectorTest.scala") << """
            object KingProjectorTest {
                class A[X[_]]
                new A[Map[Int, *]] // this expression requires kind projector
            }"""

        expect:
        succeeds("compileScala")
    }

    def "compile bad scala code do not fail the build when options.failOnError is false"() {
        given:
        badCode()

        and:
        buildFile << "compileScala.options.failOnError = false\n"

        expect:
        succeeds 'compileScala'
    }

    def "compile bad scala code do not fail the build when scalaCompileOptions.failOnError is false"() {
        given:
        badCode()

        and:
        buildFile << "compileScala.scalaCompileOptions.failOnError = false\n"

        expect:
        succeeds 'compileScala'
    }

    def "joint compile bad java code do not fail the build when options.failOnError is false"() {
        given:
        goodCode()
        badJavaCode()

        and:
        buildFile << "compileScala.options.failOnError = false\n"

        expect:
        succeeds 'compileScala'
    }

    def "joint compile bad java code do not fail the build when scalaCompileOptions.failOnError is false"() {
        given:
        goodCode()
        badJavaCode()

        and:
        buildFile << "compileScala.scalaCompileOptions.failOnError = false\n"

        expect:
        succeeds 'compileScala'
    }

    def compileBadCodeWithoutFailing() {
        given:
        badCode()

        and:
        buildFile <<
            """
compileScala.scalaCompileOptions.failOnError = false
"""

        expect:
        succeeds("compileScala")
        result.assertHasErrorOutput("type mismatch")
        scalaClassFile("").assertHasDescendants()
    }

    def "respects fork options settings and executable"() {
        def differentJvm = AvailableJavaHomes.differentJdk
        Assume.assumeThat(differentJvm, notNullValue())
        def differentJavaExecutablePath = normaliseFileSeparators(differentJvm.javaExecutable.absolutePath)
        def differentJavaExecutableCanonicalPath = escapeString(differentJvm.javaExecutable.canonicalPath)

        file("build.gradle") << """
            import org.gradle.workers.internal.WorkerDaemonClientsManager
            import org.gradle.internal.jvm.Jvm

            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.scala-lang:scala-library:2.11.12'
            }

            java {
                sourceCompatibility = JavaVersion.${differentJvm.javaVersion.name()}
                targetCompatibility = JavaVersion.${differentJvm.javaVersion.name()}
            }

            tasks.withType(ScalaCompile) {
                options.forkOptions.executable = "${differentJavaExecutablePath}"
                options.forkOptions.memoryInitialSize = "128m"
                options.forkOptions.memoryMaximumSize = "256m"
                options.forkOptions.jvmArgs = ["-Dfoo=bar"]

                doLast {
                    assert services.get(WorkerDaemonClientsManager).idleClients.find {
                        new File(it.forkOptions.javaForkOptions.executable).canonicalPath == "${differentJavaExecutableCanonicalPath}" &&
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

    def compileWithSpecifiedEncoding() {
        given:
        goodCodeEncodedWith("ISO8859_7")

        and:
        buildFile <<
            """
apply plugin: "application"
application.mainClass = "Main"
compileScala.scalaCompileOptions.encoding = "ISO8859_7"
"""

        expect:
        succeeds("run")
        file("encoded.out").getText("utf-8") == "\u03b1\u03b2\u03b3"
    }

    def compilesWithSpecifiedDebugSettings() {
        given:
        goodCode()

        when:
        run("compileScala")

        then:
        def fullDebug = classFile("compile/test/Person.class")
        fullDebug.debugIncludesSourceFile
        fullDebug.debugIncludesLineNumbers
        fullDebug.debugIncludesLocalVariables

        when:
        buildFile <<
            """
compileScala.scalaCompileOptions.debugLevel = "line"
"""
        run("compileScala")

        then:
        def linesOnly = classFile("compile/test/Person.class")
        linesOnly.debugIncludesSourceFile
        linesOnly.debugIncludesLineNumbers
        !linesOnly.debugIncludesLocalVariables

        // older versions of scalac Ant task don't handle 'none' correctly
        if (versionNumber < VersionNumber.parse("2.10.0-AAA")) {
            return
        }

        when:
        buildFile <<
            """
compileScala.scalaCompileOptions.debugLevel = "none"
"""
        run("compileScala")

        then:
        def noDebug = classFile("compile/test/Person.class")
        !noDebug.debugIncludesLineNumbers
        !noDebug.debugIncludesSourceFile
        !noDebug.debugIncludesLocalVariables
    }

    def buildScript() {
        """
apply plugin: "scala"

repositories {
    ${mavenCentralRepositoryDefinition()}
}

dependencies {
    implementation "org.scala-lang:scala-library:$version"
}
"""
    }

    def goodCode() {
        file("src/main/scala/compile/test/Person.scala") <<
            """
package compile.test

class Person(val name: String, val age: Int) {
    def hello(): List[Int] = List(3, 1, 2)
}
"""
        file("src/main/scala/compile/test/Person2.scala") <<
            """
package compile.test

class Person2(name: String, age: Int) extends Person(name, age) {
}
"""
    }

    def goodCodeEncodedWith(String encoding) {
        def code =
            """
import java.io.{FileOutputStream, File, OutputStreamWriter}

object Main {
    def main(args: Array[String]): Unit = {
        // Some lowercase greek letters
        val content = "\u03b1\u03b2\u03b3"
        val writer = new OutputStreamWriter(new FileOutputStream(new File("encoded.out")), "utf-8")
        writer.write(content)
        writer.close()
    }
}
"""
        def file = file("src/main/scala/Main.scala")
        file.parentFile.mkdirs()
        file.withWriter(encoding) { writer ->
            writer.write(code)
        }

        // Verify some assumptions: that we've got the correct characters in there, and that we're not using the system encoding
        assert code.contains(new String(Character.toChars(0x3b1)))
        assert !Arrays.equals(code.bytes, file.bytes)
    }

    def badCode() {
        file("src/main/scala/compile/test/Person.scala") <<
            """
package compile.test

class Person(val name: String, val age: Int) {
    def hello() : String = 42
}
"""
    }

    def badJavaCode() {
        file("src/main/scala/compile/test/Something.java") << """
            package compile.test;
            public class Something extends {}
        """.stripIndent()
    }

    def classFile(String path) {
        return new ClassFile(scalaClassFile(path))
    }

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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

    def classHash(File file) {
        def dir = file.parentFile
        def name = file.name - '.class'
        def hasher = Hashing.md5().newHasher()
        dir.listFiles().findAll { it.name.startsWith(name) && it.name.endsWith('.class') }.sort().each {
            hasher.putBytes(it.bytes)
        }
        hasher.hash()
    }
}
