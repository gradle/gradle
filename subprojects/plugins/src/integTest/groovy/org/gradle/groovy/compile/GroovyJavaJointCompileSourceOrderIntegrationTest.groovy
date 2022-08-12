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
import spock.lang.Issue

class GroovyJavaJointCompileSourceOrderIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://issues.apache.org/jira/browse/GROOVY-7966")
    def "compiling Groovy has the same results with reversed file order"() {
        file("src/main/groovy/JavaThing.java") << "public class JavaThing {}"
        file("src/main/groovy/AbstractThing.groovy") << "class AbstractThing {}"
        file("src/main/groovy/Thing.groovy") << "class Thing extends AbstractThing {}"

        def resultClass = file("build/classes/Thing.class")

        buildFile.text = buildFileWithSources("AbstractThing.groovy", "Thing.groovy", "JavaThing.java")
        succeeds "compile"
        def originalBytes = resultClass.bytes

        assert file("build").deleteDir()

        when:
        buildFile.text = buildFileWithSources("AbstractThing.groovy", "JavaThing.java", "Thing.groovy")
        succeeds "compile"
        def reversedBytes = resultClass.bytes

        then:
        reversedBytes == originalBytes

        assert file("build").deleteDir()

        when:
        buildFile.text = buildFileWithSources("Thing.groovy", "AbstractThing.groovy", "JavaThing.java")
        succeeds "compile"
        def reversedAgainBytes = resultClass.bytes

        then:
        reversedAgainBytes == originalBytes
    }

    def "groovy and java source directory compilation order can be reversed (task configuration #configurationStyle)"() {
        given:
        file("src/main/groovy/Groovy.groovy") << "class Groovy { }"
        file("src/main/java/Java.java") << "public class Java { Groovy groovy = new Groovy(); }"
        buildFile << """
            plugins {
                id 'groovy'
            }
            $setup
            tasks.named('compileGroovy') {
                classpath = sourceSets.main.compileClasspath
            }
            dependencies {
                implementation localGroovy()
            }
        """

        when:
        succeeds 'compileJava'

        then:
        result.assertTasksExecutedInOrder(':compileGroovy', ':compileJava')

        where:
        configurationStyle | setup
        'lazy'             | "tasks.named('compileJava') { classpath += files(sourceSets.main.groovy.classesDirectory) }"
        'eager'            | "compileJava { classpath += files(sourceSets.main.groovy.classesDirectory) }"
    }

    def "groovy and java source directory compilation order can be reversed for a custom source set"() {
        given:
        file("src/main/groovy/Groovy.groovy") << "class Groovy { }"
        file("src/main/java/Java.java") << "public class Java { Groovy groovy = new Groovy(); }"
        buildFile << """
            plugins {
                id 'groovy'
            }
            sourceSets {
                mySources
            }

            tasks.named('compileMySourcesJava') {
                classpath += files(sourceSets.mySources.groovy.classesDirectory)
            }
            tasks.named('compileMySourcesGroovy') {
                classpath = sourceSets.mySources.compileClasspath
            }
            dependencies {
                mySourcesImplementation localGroovy()
            }
        """

        when:
        succeeds 'compileMySourcesJava'

        then:
        result.assertTasksExecutedInOrder(':compileMySourcesGroovy', ':compileMySourcesJava')
    }

    private static String buildFileWithSources(String... sourceFiles) {
        """
            configurations {
                compile
            }

            dependencies {
                compile localGroovy()
            }

            task compile(type: GroovyCompile) {
                source ${sourceFiles.collect { "'src/main/groovy/$it'" }.join(", ")}
                classpath = configurations.compile
                groovyClasspath = configurations.compile
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDirectory = file("\$buildDir/classes")
            }
        """
    }
}
