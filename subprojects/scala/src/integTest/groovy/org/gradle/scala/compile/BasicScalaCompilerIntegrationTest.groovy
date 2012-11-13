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

import org.gradle.integtests.fixtures.ClassFile
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.util.VersionNumber

abstract class BasicScalaCompilerIntegrationTest extends MultiVersionIntegrationSpec {
    def setup() {
        args("-i", "-PscalaVersion=$version")
        buildFile << buildScript()
        buildFile <<
"""
DeprecationLogger.whileDisabled {
    ${compilerConfiguration()}
}
"""
    }

    def compileGoodCode() {
        given:
        goodCode()

        expect:
        succeeds("compileScala")
        output.contains(logStatement())
        file("build/classes/main/compile/test/Person.class").exists()
    }

    def compileBadCodeBreaksTheBuild() {
        given:
        badCode()

        expect:
        fails("compileScala")
        output.contains(logStatement())
        errorOutput.contains("type mismatch")
        file("build/classes/main").assertHasDescendants()
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
        file("build/classes/main").assertHasDescendants()
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
        def fullDebug = classFile("build/classes/main/compile/test/Person.class")
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
        def linesOnly = classFile("build/classes/main/compile/test/Person.class")
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
        run("compileScala")

        then:
        def noDebug = classFile("build/classes/main/compile/test/Person.class")
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
    scalaTools "org.scala-lang:scala-compiler:$version"
    compile "org.scala-lang:scala-library:$version"
    compile localGroovy()
}
"""
    }

    abstract String compilerConfiguration()

    abstract String logStatement()

    def goodCode() {
        file("src/main/scala/compile/test/Person.scala") <<
"""
package compile.test

import scala.collection.JavaConversions._
import org.codehaus.groovy.runtime.DefaultGroovyMethods

class Person(val name: String, val age: Int) {
    def hello() {
        val x: java.util.Collection[Int] = List(3, 1, 2)
        DefaultGroovyMethods.max(x)
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

    def classFile(String path) {
        return new ClassFile(file(path))
    }
}

