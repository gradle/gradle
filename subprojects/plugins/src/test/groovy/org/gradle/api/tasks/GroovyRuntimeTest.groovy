/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.tasks

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.util.HelperUtil

import spock.lang.Specification

class GroovyRuntimeTest extends Specification {
    def project = HelperUtil.createRootProject()

    def setup() {
        project.plugins.apply(GroovyBasePlugin)
    }

    def "inferred Groovy class path uses file dependency if 'groovy-all' Jar is found"() {
        when:
        def classpath = project.groovyRuntime.inferGroovyClasspath([new File("other.jar"), new File("groovy-all-2.1.2${classifier}.jar")])

        then:
        classpath instanceof FileCollection
        classpath.singleFile.name == "groovy-all-2.1.2${classifier}.jar"

        where:
        classifier << ["", "-indy"]
    }

    def "inferred Groovy class path uses repository dependency if 'groovy' Jar is found (to get transitive dependencies right)"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def classpath = project.groovyRuntime.inferGroovyClasspath([new File("other.jar"), new File("groovy-2.1.2${classifier}.jar")])

        then:
        classpath instanceof Configuration
        classpath.state == Configuration.State.UNRESOLVED
        classpath.dependencies.size() == 2
        classpath.dependencies.any { it.group == "org.codehaus.groovy" && it.name == "groovy" && it.version == "2.1.2" } // not sure how to check classifier
        classpath.dependencies.any { it.group == "org.codehaus.groovy" && it.name == "groovy-ant" && it.version == "2.1.2" } // not sure how to check classifier

        where:
        classifier << ["", "-indy"]
    }

    def "inferred Groovy class path falls back to 'groovy' configuration if the latter is explicitly configured"() {
        project.dependencies {
            groovy project.files("my-groovy.jar")
        }

        when:
        def classpath = project.groovyRuntime.inferGroovyClasspath([new File("other.jar"), new File("groovy-all-2.1.2.jar")])

        then:
        classpath.is(project.configurations.groovy)
        classpath.singleFile.name == "my-groovy.jar"
    }

    def "inferred Groovy class path falls back to 'groovy' configuration if no Groovy Jar found on class path"() {
        when:
        def classpath = project.groovyRuntime.inferGroovyClasspath([new File("other.jar"), new File("other2.jar")])

        then:
        classpath.is(project.configurations.groovy)
    }

    def "inferred Groovy class path falls back to 'groovy' configuration if 'groovy' Jar found and no repository declared"() {
        when:
        def classpath = project.groovyRuntime.inferGroovyClasspath([new File("other.jar"), new File("groovy-2.1.2.jar")])

        then:
        classpath.is(project.configurations.groovy)
    }
}
