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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestFile

class CachedGroovyCompileIntegrationTest extends AbstractIntegrationSpec {

    File cacheDir

    def setup() {
        // Make sure cache dir is empty for every test execution
        cacheDir = temporaryFolder.file("cache-dir").deleteDir().createDir()
        setupProjectInDirectory()
    }

    def setupProjectInDirectory(TestFile project = temporaryFolder.testDirectory) {
        project.with {
            file('build.gradle') << """
            plugins {
                id 'groovy'
                id 'application'
            }

            mainClassName = "Hello"

            repositories {
                mavenCentral()
            }

            dependencies {
                compile 'org.codehaus.groovy:groovy-all:2.4.7'
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

    def 'groovy compilation can be cached'() {
        when:
        succeedsWithCache 'compileGroovy'

        then:
        skippedTasks == ([':compileJava'] as Set)

        when:
        succeedsWithCache 'clean', 'run'

        then:
        groovyCompileIsCached()
    }

    def "compilation is cached if the build executed from a different directory"() {
        // Compile Groovy in a different copy of the project
        def remoteProjectDir = file("remote-project")
        setupProjectInDirectory(remoteProjectDir)

        when:
        executer.inDirectory(remoteProjectDir)
        succeedsWithCache "compileGroovy"
        then:
        !skippedTasks.contains(':compileGroovy')
        remoteProjectDir.file("build/classes/main/Hello.class").exists()

        // Remove the project completely
        remoteProjectDir.deleteDir()

        when:
        succeedsWithCache "compileGroovy"
        then:
        groovyCompileIsCached()
    }

    def "compilation is cached if location of the Groovy library is different"() {
        given:
        populateCache()

        executer.requireOwnGradleUserHomeDir() // dependency will be downloaded into a different directory

        when:
        succeedsWithCache "compileGroovy"

        then:
        groovyCompileIsCached()
    }

    def "compilation is not cached if we change the version of the Groovy library"() {
        given:
        populateCache()
        buildFile.text = """
            plugins { id 'groovy' }

            repositories { mavenCentral() }
            dependencies { compile 'org.codehaus.groovy:groovy-all:2.4.5' }
        """.stripIndent()

        when:
        succeedsWithCache 'compileGroovy'

        then:
        groovyCompileIsNotCached()
    }

    void groovyCompileIsCached() {
        assert skippedTasks.contains(":compileGroovy")
        assert file("build/classes/main/Hello.class").exists()
    }

    void groovyCompileIsNotCached() {
        assert !skippedTasks.contains(":compileGroovy")
    }

    def populateCache() {
        def remoteProjectDir = file("remote-project")
        setupProjectInDirectory(remoteProjectDir)
        executer.inDirectory(remoteProjectDir)
        succeedsWithCache "compileGroovy"

        assert !skippedTasks.contains(':compileGroovy')
        // Remove the project completely
        remoteProjectDir.deleteDir()
    }

    def succeedsWithCache(String... tasks) {
        enableCache()
        succeeds tasks
    }

    private GradleExecuter enableCache() {
        executer.withArgument "-Dorg.gradle.cache.tasks=true"
        executer.withArgument "-Dorg.gradle.cache.tasks.directory=" + cacheDir.absolutePath
    }
}
