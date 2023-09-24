/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.ClassFile
import org.gradle.util.internal.VersionNumber
import org.junit.Assume
import org.junit.Rule

import static org.gradle.scala.ScalaCompilationFixture.scalaDependency

abstract class BasicZincScalaCompilerIntegrationTest extends MultiVersionIntegrationSpec {
    @Rule
    TestResources testResources = new TestResources(temporaryFolder)

    def setup() {
        args("-PscalaVersion=$version")
        buildFile << buildScript()
        executer.withRepositoryMirrors()
    }

    def compileGoodCode() {
        goodCode()

        expect:
        succeeds("compileScala")
        scalaClassFile("compile/test/Person.class").exists()
    }

    def compileBadCode() {
        badCode()

        expect:
        fails("compileScala")
        file("build/classes/scala/main").assertIsEmptyDir()

        if (versionNumber.major >= 3) {
            result.assertHasErrorOutput("src/main/scala/compile/test/Person.scala:4:28: Found:    (42 : Int)\nRequired: String")
        } else {
            result.assertHasErrorOutput("src/main/scala/compile/test/Person.scala:4:28: type mismatch;\n found   : Int(42)\n required: String")
        }
    }

    def useCompilerPluginIfDefined() {
        // https://docs.scala-lang.org/scala3/guides/migration/plugin-kind-projector.html
        Assume.assumeTrue(
            VersionNumber.parse("2.12.14") <= versionNumber && versionNumber < VersionNumber.parse("2.13.0") ||
                VersionNumber.parse("2.13.6") <= versionNumber && versionNumber < VersionNumber.parse("3.0.0")
        )

        given:
        file("build.gradle") << """
            dependencies {
                scalaCompilerPlugins "org.typelevel:kind-projector_$version:0.13.2"
            }
        """

        file("src/main/scala/KingProjectorTest.scala") << """
            object KingProjectorTest {
                class A[X[_]]
                new A[Map[Int, *]] // this expression requires kind projector
            }
        """

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
        badCode()

        buildFile << """
            compileScala.scalaCompileOptions.failOnError = false
        """

        expect:
        succeeds("compileScala")
        file("build/classes/scala/main").assertIsEmptyDir()

        if (versionNumber.major >= 3) {
            result.assertHasErrorOutput("src/main/scala/compile/test/Person.scala:4:28: Found:    (42 : Int)\nRequired: String")
        } else {
            result.assertHasErrorOutput("src/main/scala/compile/test/Person.scala:4:28: type mismatch;\n found   : Int(42)\n required: String")
        }
    }

    def compileWithSpecifiedEncoding() {
        given:
        goodCodeEncodedWith("ISO8859_7")

        and:
        buildFile << """
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

        // Scala 3 does not support `-g` option configured via `scalaCompileOptions.debugLevel`
        if (versionNumber.major >= 3) {
            return
        }

        when:
        buildFile << """
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
        buildFile << """
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

            ${mavenCentralRepository()}

            dependencies {
                implementation "${scalaDependency(version.toString())}"
            }
        """
    }

    def goodCode() {
        file("src/main/scala/compile/test/Person.scala") << """
            package compile.test

            /**
            * A person.
            * Can live in a house.
            * Has a name and an age.
            */
            class Person(val name: String, val age: Int) {
                def hello(): List[Int] = List(3, 1, 2)
            }
        """.stripIndent()

        file("src/main/scala/compile/test/Person2.scala") << """
            package compile.test

            class Person2(name: String, age: Int) extends Person(name, age) {
            }
        """.stripIndent()
    }

    def goodCodeEncodedWith(String encoding) {
        def code = """
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
        """.stripIndent()
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
        file("src/main/scala/compile/test/Person.scala") << """package compile.test

class Person(val name: String, val age: Int) {
    def hello() : String = 42
}"""
    }

    def goodCodeUsingJavaInterface() {
        file("src/main/scala/compile/test/Demo.scala") << """
            package compile.test

            object Demo {
              MyInterface.helloWorld();
            }
        """.stripIndent()
    }


    def goodJavaInterfaceCode() {
        file("src/main/java/compile/test/MyInterface.java") << """
            package compile.test;
            public interface MyInterface {
                 default void defaultMethod() {
                    System.out.println("Hello World!");
                 }

                 static void helloWorld() {
                    System.out.println("Hello World!");
                 }
            }
        """.stripIndent()
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
