/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.java.compile.fork

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources

import org.junit.Rule
import org.hamcrest.Matchers

class ForkingJavaCompilerIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources resources = new TestResources()

    def compileGoodCode() {
        expect:
        succeeds("compileJava")
        !errorOutput
        file("build/classes/main/compile/fork/Person.class").exists()
    }
    
    def compileBadCode() {
        expect:
        fails("compileJava")
        errorOutput.contains("';' expected")
        !file("build/classes/main/compile/fork/Person.class").exists()
    }

    def compileBadCodeWithoutFailing() {
        expect:
        succeeds("compileJava")
        errorOutput.contains("';' expected")
        !file("build/classes/main/compile/fork/Person.class").exists()
    }
    
    def compileWithLongClasspath() {
        expect:
        succeeds("compileJava")
        !errorOutput
        file("build/classes/main/compile/fork/Person.class").exists()
    }

    def compileWithCustomHeapSettings() {
        expect:
        succeeds("compileJava")
        !errorOutput
        file("build/classes/main/compile/fork/Person.class").exists()
        // couldn't find a good way to verify that heap settings take effect
    }

    def useAntForking() {
        expect:
        succeeds("compileJava")
        !errorOutput
        file("build/classes/main/compile/fork/Person.class").exists()
    }
    
    def listSourceFiles() {
        expect:
        succeeds("compileJava")
        output.contains(new File("src/main/java/compile/fork/Person1.java").toString())
        output.contains(new File("src/main/java/compile/fork/Person2.java").toString())
        !errorOutput
        file("build/classes/main/compile/fork/Person1.class").exists()
        file("build/classes/main/compile/fork/Person2.class").exists()
    }
    
    def nonJavaSourceFilesAreNotTolerated() {
        expect:
        fails("compileJava")
        failure.assertThatCause(Matchers.startsWith("Cannot compile non-Java source file"))
    }
}
