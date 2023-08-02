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
package org.gradle.groovy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class GroovyBasePluginIntegrationTest extends AbstractIntegrationSpec {
    def "defaults Groovy class path to inferred Groovy dependency"() {
        file("build.gradle") << """
apply plugin: "groovy-base"

sourceSets {
    custom
}

${mavenCentralRepository()}

dependencies {
    customImplementation "$dependency"
}

task groovydoc(type: Groovydoc) {
    classpath = sourceSets.custom.runtimeClasspath
}

task verify {
    def compileCustomGroovyClasspath = compileCustomGroovy.groovyClasspath
    def groovydocGroovyClasspath = groovydoc.groovyClasspath
    doLast {
        assert compileCustomGroovyClasspath.files.any { it.name == "$jarFile" }
        assert groovydocGroovyClasspath.files.any { it.name == "$jarFile" }
    }
}
"""

        expect:
        succeeds("verify")

        where:
        dependency                                   | jarFile
        "org.codehaus.groovy:groovy-all:2.4.10"      | "groovy-all-2.4.10.jar"
        "org.codehaus.groovy:groovy:2.4.10"          | "groovy-2.4.10.jar"
        "org.codehaus.groovy:groovy-all:2.4.10:indy" | "groovy-all-2.4.10-indy.jar"
    }

    def "only resolves source class path feeding into inferred Groovy class path if/when the latter is actually used (but not during autowiring)"() {
        file("build.gradle") << """
apply plugin: "groovy-base"

sourceSets {
    custom
}

${mavenCentralRepository()}

dependencies {
    customImplementation "org.codehaus.groovy:groovy-all:2.4.10"
}

task groovydoc(type: Groovydoc) {
    classpath = sourceSets.custom.runtimeClasspath
}

task verify {
    def customCompileClasspathState = provider {
        configurations.customCompileClasspath.state.toString()
    }
    def customRuntimeClasspathState = provider {
        configurations.customRuntimeClasspath.state.toString()
    }
    doLast {
        assert customCompileClasspathState.get() == "UNRESOLVED"
        assert customRuntimeClasspathState.get() == "UNRESOLVED"
    }
}
        """

        expect:
        succeeds("verify")
    }

    def "not specifying a groovy runtime produces decent error message"() {
        given:
        buildFile << """
            apply plugin: "groovy-base"

            sourceSets {
                main {}
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation "com.google.guava:guava:11.0.2"
            }
        """

        file("src/main/groovy/Thing.groovy") << """
            class Thing {}
        """

        when:
        fails "compileGroovy"

        then:
        failure.assertHasDescription("Execution failed for task ':compileGroovy'.")
        failure.assertHasCause "Cannot infer Groovy class path because no Groovy Jar was found on class path: "
    }

    @Issue("https://github.com/gradle/gradle/issues/5722")
    def "can override sourceSet language destinationDirectory to override compile task destinationDirectory"() {
        given:
        buildFile << '''
            apply plugin: 'groovy-base'

            sourceSets {
                main {
                    groovy.destinationDirectory.set(file("$buildDir/bin"))
                }
            }

            task assertDirectoriesAreEquals {
                def mainGroovySourceDirSet = sourceSets.main.groovy
                def mainGroovyDestDir = mainGroovySourceDirSet.destinationDirectory
                def compileGroovyDestinationDir = compileGroovy.destinationDirectory
                def binDir = file("$buildDir/bin")
                doLast {
                    assert mainGroovyDestDir.get().asFile == compileGroovyDestinationDir.get().asFile
                    assert mainGroovyDestDir.get().asFile == binDir
                }
            }
        '''

        expect:
        succeeds 'assertDirectoriesAreEquals'
    }
}
