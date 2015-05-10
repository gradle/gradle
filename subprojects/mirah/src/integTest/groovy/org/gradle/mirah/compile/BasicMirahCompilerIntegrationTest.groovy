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

package org.gradle.mirah.compile

import spock.lang.Ignore

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.test.fixtures.file.ClassFile
import org.gradle.util.VersionNumber

abstract class BasicMirahCompilerIntegrationTest extends MultiVersionIntegrationSpec {
    def setup() {
        args("-i", "-PmirahVersion=$version")
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
        succeeds("compileMirah")
        output.contains(logStatement())
        file("build/classes/main/compile/test/Person.class").exists()
    }

    def compileBadCode() {
        given:
        badCode()

        expect:
        fails("compileMirah")
        output.contains(logStatement())
        errorOutput.contains("Invalid return type int")
        file("build/classes/main").assertHasDescendants()
    }

    @Ignore // org.mirah:mirah:0.1.4 currently throws a "java.lang.UnsupportedOperationException: Compilation failed" instead of returning an error code.
    def compileBadCodeWithoutFailing() {
        given:
        badCode()

        and:
        buildFile <<
"""
compileMirah.mirahCompileOptions.failOnError = false
"""

        expect:
        succeeds("compileMirah")
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
compileMirah.mirahCompileOptions.encoding = "ISO8859_7"
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
        run("compileMirah")

        then:
        def fullDebug = classFile("build/classes/main/compile/test/Person.class")
        fullDebug.debugIncludesSourceFile
        fullDebug.debugIncludesLineNumbers
        fullDebug.debugIncludesLocalVariables
        // Currently, we always have full debug information.
    }

    def failsWithGoodErrorMessageWhenMirahToolsNotConfigured() {

    }

    def buildScript() {
"""
apply plugin: "mirah"

repositories {
    mavenCentral()
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath "org.mirah:mirah:$version"
    }
}
"""
    }

    abstract String compilerConfiguration()

    abstract String logStatement()

    def goodCode() {
        file("src/main/mirah/compile/test/Person.mirah") <<
"""
package compile.test

import mirah.collection.JavaConversions._

class Person(val name: String, val age: Int) {
    def hello() {
        val x: java.util.List[Int] = List(3, 1, 2)
        java.util.Collections.reverse(x)
    }
}
"""
        file("src/main/mirah/compile/test/Person2.mirah") <<
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
        def file = file("src/main/mirah/Main.mirah")
        file.parentFile.mkdirs()
        file.withWriter(encoding) { writer ->
            writer.write(code)
        }

        // Verify some assumptions: that we've got the correct characters in there, and that we're not using the system encoding
        assert code.contains(new String(Character.toChars(0x3b1)))
        assert !Arrays.equals(code.bytes, file.bytes)
    }

    def badCode() {
        file("src/main/mirah/compile/test/Person.mirah") <<
"""
package compile.test

class Person
  def hello:String
    42
  end
end
"""
    }

    def classFile(String path) {
        return new ClassFile(file(path))
    }
}

