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


package org.gradle.java.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class BasicJavaCompilerIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        executer.withArguments("-i")
        buildFile << buildScript()
        buildFile << compilerConfiguration()
    }

    def compileGoodCode() {
        given:
        goodCode()

        expect:
        succeeds("compileJava")
        output.contains(logStatement())
        !errorOutput
        file("build/classes/main/compile/test/Person.class").exists()
    }

    def compileBadCodeBreaksTheBuild() {
        given:
        badCode()

        expect:
        fails("compileJava")
        output.contains(logStatement())
        compilerErrorOutput.contains("';' expected")
        file("build/classes/main").assertHasDescendants()
    }

    def compileBadCodeWithoutFailing() {
        given:
        badCode()

        and:
        buildFile << 'compileJava.options.failOnError = false'

        expect:
        succeeds("compileJava")
        output.contains(logStatement())
        compilerErrorOutput.contains("';' expected")
        file("build/classes/main").assertHasDescendants()
    }

    def compileWithSpecifiedEncoding() {
        given:
        goodCodeEncodedWith('ISO8859_7')

        and:
        buildFile << '''
            apply plugin: 'application'
            mainClassName = 'Main'
            compileJava.options.encoding = \'ISO8859_7\'
'''

        expect:
        succeeds("run")
        output.contains(logStatement())
        !errorOutput
        file('encoded.out').getText("utf-8") == "\u03b1\u03b2\u03b3"
    }

    def getCompilerErrorOutput() {
        return errorOutput
    }

    def buildScript() {
        '''
apply plugin: "java"

dependencies {
    compile localGroovy()
}
'''
    }

    abstract compilerConfiguration()

    abstract logStatement()

    def goodCode() {
        file("src/main/java/compile/test/Person.java") << '''
package compile.test;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.Arrays;

public class Person {
    String name;
    int age;

    void hello() {
        DefaultGroovyMethods.max(Arrays.asList(3, 1, 2));
    }
}'''
        file("src/main/java/compile/test/Person2.java") << '''
package compile.test;

class Person2 extends Person {
}
'''
    }

    def goodCodeEncodedWith(String encoding) {
        def code = '''
import java.io.FileOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;

class Main {
    public static void main(String[] args) throws Exception {
        // Some lowercase greek letters
        String content = "\u03b1\u03b2\u03b3";
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(new File("encoded.out")), "utf-8");
        writer.write(content);
        writer.close();
    }
}
'''
        def file = file("src/main/java/Main.java")
        file.parentFile.mkdirs()
        file.withWriter(encoding) { writer ->
            writer.write(code)
        }

        // Verify some assumptions: that we've got the correct characters in there, and that we're not using the system encoding
        assert code.contains(new String(Character.toChars(0x3b1)))
        assert !Arrays.equals(code.bytes, file.bytes)
    }

    def badCode() {
        file("src/main/java/compile/test/Person.java") << '''
        package compile.fork;

        public class Person {
            String name;
            int age;

            void hello() {
                return nothing
            }
        } '''
    }
}
