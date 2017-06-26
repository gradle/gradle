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


import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.test.fixtures.file.ClassFile
import org.gradle.util.VersionNumber

abstract class BasicScalaCompilerIntegrationTest extends MultiVersionIntegrationSpec {
    def setup() {
        args("-i", "-PscalaVersion=$version")
        buildFile << buildScript()

        // We've deprecated some getters that are used by all scala compiler calls.
        executer.expectDeprecationWarning()
    }

    def compileGoodCode() {
        given:
        goodCode()

        expect:
        succeeds("compileScala")
        output.contains(logStatement())
        scalaClassFile("compile/test/Person.class").exists()
    }

    def compileBadCode() {
        given:
        badCode()

        expect:
        fails("compileScala")
        output.contains(logStatement())
        errorOutput.contains("type mismatch")
        scalaClassFile("").assertHasDescendants()
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
        output.contains(logStatement())
        errorOutput.contains("type mismatch")
        scalaClassFile("").assertHasDescendants()
    }

    def compileWithSpecifiedEncoding() {
        given:
        goodCodeEncodedWith("ISO8859_7")

        and:
        buildFile <<
"""
apply plugin: "application"
mainClassName = "Main"
compileScala.scalaCompileOptions.encoding = "ISO8859_7"
"""

        expect:
        succeeds("run")
        output.contains(logStatement())
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
        // This resets every time run is called.
        executer.expectDeprecationWarning()
        run("compileScala")

        then:
        def linesOnly = classFile("compile/test/Person.class")
        linesOnly.debugIncludesSourceFile
        linesOnly.debugIncludesLineNumbers
        !linesOnly.debugIncludesLocalVariables

        // older versions of scalac Ant task don't handle 'none' correctly
        if (versionNumber < VersionNumber.parse("2.10.0-AAA")) { return }

        when:
        buildFile <<
"""
compileScala.scalaCompileOptions.debugLevel = "none"
"""
        // This resets every time run is called.
        executer.expectDeprecationWarning()
        run("compileScala")

        then:
        def noDebug = classFile("compile/test/Person.class")
        !noDebug.debugIncludesLineNumbers
        !noDebug.debugIncludesSourceFile
        !noDebug.debugIncludesLocalVariables
    }

    def failsWithGoodErrorMessageWhenScalaToolsNotConfigured() {

    }

    def buildScript() {
"""
apply plugin: "scala"

repositories {
    mavenCentral()
    // temporary measure for the next few hours, until Zinc 0.2-M2 has landed in Central
    maven { url "https://oss.sonatype.org/content/repositories/releases" }
}

dependencies {
    compile "org.scala-lang:scala-library:$version"
}
"""
    }

    abstract String logStatement()

    def goodCode() {
        file("src/main/scala/compile/test/Person.scala") <<
"""
package compile.test

import scala.collection.JavaConversions._

class Person(val name: String, val age: Int) {
    def hello() {
        val x: java.util.List[Int] = List(3, 1, 2)
        java.util.Collections.reverse(x)
    }
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
    def main(args: Array[String]) {
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
}

