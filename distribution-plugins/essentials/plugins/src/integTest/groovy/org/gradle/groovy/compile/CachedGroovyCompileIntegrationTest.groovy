/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.groovy.compile

import org.gradle.api.tasks.compile.AbstractCachedCompileIntegrationTest
import org.gradle.test.fixtures.file.TestFile

class CachedGroovyCompileIntegrationTest extends AbstractCachedCompileIntegrationTest {
    String compilationTask = ':compileGroovy'
    String compiledFile = "build/classes/groovy/main/Hello.class"

    @Override
    def setupProjectInDirectory(TestFile project) {
        project.with {
            file('settings.gradle') << localCacheConfiguration()
            file('build.gradle').text = """
            plugins {
                id 'groovy'
                id 'application'
            }

            application {
                mainClass = "Hello"
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.codehaus.groovy:groovy-all:2.4.10'
            }
        """.stripIndent()

        file('src/main/groovy/Hello.groovy') << """
            class Hello {
                public static void main(String... args) {
                    println "Hello!"
                }
            }
        """.stripIndent()
        }
    }

    def "compilation is cached if location of the Groovy library is different"() {
        given:
        populateCache()

        executer.requireOwnGradleUserHomeDir() // dependency will be downloaded into a different directory

        when:
        withBuildCache().run compilationTask

        then:
        compileIsCached()
    }

    def "compilation is not cached if we change the version of the Groovy library"() {
        given:
        populateCache()
        buildFile.text = """
            plugins { id 'groovy' }

            ${mavenCentralRepository()}
            dependencies { implementation 'org.codehaus.groovy:groovy-all:2.4.5' }
        """.stripIndent()

        when:
        withBuildCache().run compilationTask

        then:
        compileIsNotCached()
    }

    def "joint Java and Groovy compilation can be cached"() {
        given:
        buildScript """
            plugins {
                id 'groovy'
            }

            dependencies {
                implementation localGroovy()
            }
        """
        file('src/main/java/RequiredByGroovy.java') << """
            public class RequiredByGroovy {
                public static void printSomething() {
                    java.lang.System.out.println("Hello from Java");
                }
            }
        """
        file('src/main/java/RequiredByGroovy.java').makeOlder()

        file('src/main/groovy/UsesJava.groovy') << """
            @groovy.transform.CompileStatic
            class UsesJava {
                public void printSomething() {
                    RequiredByGroovy.printSomething()
                }
            }
        """
        file('src/main/groovy/UsesJava.groovy').makeOlder()
        def compiledJavaClass = javaClassFile('RequiredByGroovy.class')
        def compiledGroovyClass = groovyClassFile('UsesJava.class')

        when:
        withBuildCache().run ':compileJava', ':compileGroovy'

        then:
        compiledJavaClass.exists()
        compiledGroovyClass.exists()

        when:
        withBuildCache().run ':clean', ':compileJava'

        then:
        skipped(':compileJava')

        when:
        // This line is crucial to expose the bug
        // When doing this and then loading the classes for
        // compileGroovy from the cache the compiled java
        // classes are replaced and recorded as changed
        compiledJavaClass.makeOlder()
        withBuildCache().run ':compileGroovy'

        then:
        skipped(':compileJava', ':compileGroovy')

        when:
        file('src/main/java/RequiredByGroovy.java').text = """
            public class RequiredByGroovy {
                public static void printSomethingNew() {
                    java.lang.System.out.println("Hello from Java");
                    // Different
                }
            }
        """
        file('src/main/groovy/UsesJava.groovy').text = """
            @groovy.transform.CompileStatic
            class UsesJava {
                public void printSomething() {
                    RequiredByGroovy.printSomethingNew()
                    // Some comment
                }
            }
        """

        withBuildCache().run ':compileGroovy'

        then:
        compiledJavaClass.exists()
        compiledGroovyClass.exists()
    }
}
