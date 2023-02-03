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

import spock.lang.Issue

abstract class JavaCompilerIntegrationSpec extends BasicJavaCompilerIntegrationSpec {

    def setup() {
        buildFile << """
            tasks.withType(JavaCompile) {
                options.compilerArgs << '-Xlint:all' << '-Werror'
            }
        """
    }

    def compileWithLongClasspath() {
        given:
        goodCode()

        and:
        buildFile << '''
            dependencies {
                file("$projectDir/lib/").mkdirs()
                implementation files((1..999).collect {
                    createJarFile("$projectDir/lib/library${it}.jar")
                })
            }

            def createJarFile(String libraryPath) {
                def fos
                try {
                    fos = new FileOutputStream(file(libraryPath))
                    new java.util.jar.JarOutputStream(fos, new java.util.jar.Manifest()).withStream {
                        libraryPath
                    }
                } finally {
                    fos?.close()
                }
            }
        '''

        expect:

        succeeds("compileJava")
        output.contains(logStatement())
        javaClassFile("compile/test/Person.class").exists()
    }

    def compileWithCustomHeapSettings() {
        given:
        goodCode()

        and:
        buildFile << '''
            compileJava.options.forkOptions.with {
                memoryInitialSize = '64m'
                memoryMaximumSize = '128m'
            }
        '''

        expect:
        succeeds("compileJava")
        output.contains(logStatement())
        javaClassFile("compile/test/Person.class").exists()
        // couldn't find a good way to verify that heap settings take effect
    }

    def listSourceFiles() {
        given:
        goodCode()

        and:
        buildFile << 'compileJava.options.listFiles = true'

        expect:
        succeeds("compileJava")
        output.contains(new File("src/main/java/compile/test/Person.java").toString())
        output.contains(new File("src/main/java/compile/test/Person2.java").toString())
        output.contains(logStatement())
        javaClassFile("compile/test/Person.class").exists()
        javaClassFile("compile/test/Person2.class").exists()
    }

    def nonJavaSourceFilesAreAutomaticallyExcluded() {
        given:
        goodCode()

        and:
        file('src/main/java/resource.txt').createFile()
        buildFile << 'compileJava.source += files("src/main/java/resource.txt")'

        expect:
        succeeds("compileJava")
        javaClassFile("compile/test/Person.class").exists()
        javaClassFile("compile/test/Person2.class").exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/5750")
    def "include narrows down source files to compile"() {
        given:
        goodCode()

        and:
        file('src/main/java/Bar.java') << 'class Bar {}'
        buildFile << 'compileJava.include "**/Person*.java"'

        expect:
        succeeds("compileJava")
        javaClassFile("compile/test/Person.class").exists()
        javaClassFile("compile/test/Person2.class").exists()
        !javaClassFile("Bar.class").exists()
    }
}
