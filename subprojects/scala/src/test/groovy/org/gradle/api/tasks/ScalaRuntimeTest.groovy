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
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class ScalaRuntimeTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(ScalaBasePlugin)
    }

    def "inferred Scala class path contains 'scala-compiler' repository dependency matching 'scala-library' Jar found on class path"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def classpath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("scala-library-2.10.1.jar")])

        then:
        classpath instanceof LazilyInitializedFileCollection
        classpath.sourceCollections.size() == 1
        with(classpath.sourceCollections[0]) {
            it instanceof Configuration
            it.state == Configuration.State.UNRESOLVED
            it.dependencies.size() == 1
            with(it.dependencies.iterator().next()) {
                group == "org.scala-lang"
                name == "scala-compiler"
                version == "2.10.1"
            }
        }
    }

    def "inference fails if 'scalaTools' configuration is empty and no repository declared"() {
        when:
        def scalaClasspath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("scala-library-2.10.1.jar")])
        scalaClasspath.files

        then:
        def e = thrown GradleException
        e.message == "Cannot infer Scala class path because no repository is declared in $project"
    }

    def "inference fails if 'scalaTools' configuration is empty and no Scala library Jar is found on class path"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def scalaClasspath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("other2.jar")])
        scalaClasspath.files

        then:
        def e = thrown GradleException
        e.message.startsWith("Cannot infer Scala class path because no Scala library Jar was found. Does root project 'test' declare dependency to scala-library? Searched classpath:")
    }

    def "allows to find Scala Jar on class path"() {
        when:
        def file = project.scalaRuntime.findScalaJar([new File("other.jar"), new File("scala-jdbc-1.5.jar"), new File("scala-compiler-1.7.jar")], "jdbc")

        then:
        file.name == "scala-jdbc-1.5.jar"
    }

    def "returns null if Scala Jar not found"() {
        when:
        def file = project.scalaRuntime.findScalaJar([new File("other.jar"), new File("scala-jdbc-1.5.jar"), new File("scala-compiler-1.7.jar")], "library")

        then:
        file == null
    }

    def "allows to determine version of Scala Jar"() {
        expect:
        with(project.scalaRuntime) {
            getScalaVersion(new File("scala-compiler-2.9.2.jar")) == "2.9.2"
            getScalaVersion(new File("scala-jdbc-2.9.2.jar")) == "2.9.2"
            getScalaVersion(new File("scala-library-2.10.0-SNAPSHOT.jar")) == "2.10.0-SNAPSHOT"
            getScalaVersion(new File("scala-library-2.10.0-rc-3.jar")) == "2.10.0-rc-3"
        }
    }

    def "returns null if Scala version cannot be determined"() {
        expect:
        with(project.scalaRuntime) {
            getScalaVersion(new File("scala-compiler.jar")) == null
            getScalaVersion(new File("groovy-compiler-2.1.0.jar")) == null
        }
    }
}
