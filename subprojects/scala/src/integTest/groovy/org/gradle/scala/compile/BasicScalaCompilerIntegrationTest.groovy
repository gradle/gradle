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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.java.compile.ClassFile

abstract class BasicScalaCompilerIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withArguments("-i")
        buildFile << buildScript()
        buildFile << """
repositories {
    mavenCentral()
}

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
        output.contains("error: type mismatch")
        file("build/classes/main").assertHasDescendants()
    }

    def compileBadCodeWithoutFailing() {
        given:
        badCode()

        and:
        buildFile << 'compileScala.scalaCompileOptions.failOnError = false'

        expect:
        succeeds("compileScala")
        output.contains(logStatement())
        output.contains("error: type mismatch")
        file("build/classes/main").assertHasDescendants()
    }

    def compileWithSpecifiedEncoding() {
        given:
        goodCodeEncodedWith('ISO8859_7')

        and:
        buildFile << '''
            apply plugin: 'application'
            mainClassName = 'Main'
            compileScala.scalaCompileOptions.encoding = \'ISO8859_7\'
'''

        expect:
        succeeds("run")
        output.contains(logStatement())
        !errorOutput
        file('encoded.out').getText("utf-8") == "\u03b1\u03b2\u03b3"
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
        buildFile << """
compileScala.scalaCompileOptions.debugLevel = 'lines'
"""
        run("compileScala")

        then:
        def linesOnly = classFile("build/classes/main/compile/test/Person.class")
        // Strange, but everything except local variable info is present. Bug in scalac Ant task?
        linesOnly.debugIncludesSourceFile
        linesOnly.debugIncludesLineNumbers
        !linesOnly.debugIncludesLocalVariables

        when:
        buildFile << """
compileScala.scalaCompileOptions.debugLevel = 'none'
"""
        run("compileScala")

        then:
        def noDebug = classFile("build/classes/main/compile/test/Person.class")
        // Strange, but everything except local variable info is present. Bug in scalac Ant task?
        noDebug.debugIncludesSourceFile
        noDebug.debugIncludesLineNumbers
        !noDebug.debugIncludesLocalVariables
    }

    def failsWithGoodErrorMessageWhenScalaToolsNotConfigured() {

    }

    def getCompilerErrorOutput() {
        return errorOutput
    }

    def buildScript() {
        '''
apply plugin: "scala"

dependencies {
    scalaTools "org.scala-lang:scala-compiler:2.9.2"
    compile "org.scala-lang:scala-library:2.9.2"
    compile localGroovy()
}
'''
    }

    abstract String compilerConfiguration()

    abstract String logStatement()

    def goodCode() {
        file("src/main/scala/compile/test/Person.scala") << '''
package compile.test

import scala.collection.JavaConversions._
import org.codehaus.groovy.runtime.DefaultGroovyMethods

class Person(val name: String, val age: Int) {
    def hello() {
        val x: java.util.Collection[Int] = List(3, 1, 2)
        DefaultGroovyMethods.max(x)
    }
}'''
        file("src/main/scala/compile/test/Person2.scala") << '''
package compile.test

class Person2(name: String, age: Int) extends Person(name, age) {
}
'''
    }

    def goodCodeEncodedWith(String encoding) {
        def code = '''
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
'''
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
        file("src/main/scala/compile/test/Person.scala") << '''
        package compile.test

        class Person(val name: String, val age: Int) {
            def hello() : String = 42
        } '''
    }

    def classFile(String path) {
        return new ClassFile(file(path))
    }
}

