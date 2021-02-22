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

import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Unroll

class GroovyRuntimeTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(GroovyBasePlugin)
    }

    GroovyRuntime getGroovyRuntime() {
        project.extensions.getByType(GroovyRuntime)
    }

    def "inferred Groovy class path uses same 'groovy-all' Jar that is found on class path"() {
        when:
        def classpath = project.groovyRuntime.inferGroovyClasspath([project.file("other.jar"), project.file("groovy-all-2.1.2${classifier}.jar")])

        then:
        classpath.singleFile == project.file("groovy-all-2.1.2${classifier}.jar")

        where:
        classifier << ["", "-indy"]
    }

    def "inferred Groovy class path uses 'groovy' Jar that is found on class path for groovy 3"() {
        when:
        def classpath = project.groovyRuntime.inferGroovyClasspath([project.file("other.jar"), project.file("groovy-3.0.7.jar")])

        then:
        classpath.singleFile == project.file("groovy-3.0.7.jar")
    }

    @Unroll
    def "inferred Groovy #groovyVersion#classifier class path uses repository dependency if 'groovy' Jar is found on class path (to get transitive dependencies right)"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def classpath = project.groovyRuntime.inferGroovyClasspath([project.file("other.jar"), project.file("groovy-${groovyVersion}${classifier}.jar")])

        then:
        classpath instanceof LazilyInitializedFileCollection
        classpath.sourceCollections.size() == 1
        with(classpath.sourceCollections[0]) {
            it instanceof Configuration
            state == Configuration.State.UNRESOLVED
            dependencies.size() == expectedJars.size()
            expectedJars.each { expectedJar ->
                assert dependencies.any { it.group == "org.codehaus.groovy" && it.name == expectedJar && it.version == groovyVersion } // not sure how to check classifier
            }
        }

        where:
        groovyVersion | classifier | expectedJars
        "2.1.2"       | ""         | ["groovy", "groovy-ant"]
        "2.1.2"       | "-indy"    | ["groovy", "groovy-ant"]
        "2.5.2"       | ""         | ["groovy", "groovy-ant", "groovy-templates"]
        "2.5.2"       | "-indy"    | ["groovy", "groovy-ant", "groovy-templates"]
    }

    def "useful error message is produced when no groovy runtime could be found on a classpath"() {
        given:
        def classpath = [project.file("other.jar")]

        when:
        groovyRuntime.inferGroovyClasspath(classpath).files

        then:
        def exception = thrown(GradleException)
        exception.message.contains "no Groovy Jar was found on class path: $classpath"
    }
}
