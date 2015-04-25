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
import org.gradle.api.plugins.mirah.ScalaBasePlugin
import org.gradle.util.TestUtil
import spock.lang.Specification

class ScalaRuntimeTest extends Specification {
    def project = TestUtil.createRootProject()

    def setup() {
        project.pluginManager.apply(ScalaBasePlugin)
    }

    def "inferred Scala class path contains 'mirah-compiler' repository dependency matching 'mirah-library' Jar found on class path"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def classpath = project.mirahRuntime.inferScalaClasspath([new File("other.jar"), new File("mirah-library-2.10.1.jar")])

        then:
        classpath instanceof LazilyInitializedFileCollection
        with(classpath.delegate) {
            it instanceof Configuration
            it.state == Configuration.State.UNRESOLVED
            it.dependencies.size() == 1
            with(it.dependencies.iterator().next()) {
                group == "org.mirah-lang"
                name == "mirah-compiler"
                version == "2.10.1"
            }
        }
    }

    def "inference fails if 'mirahTools' configuration is empty and no repository declared"() {
        when:
        def mirahClasspath = project.mirahRuntime.inferScalaClasspath([new File("other.jar"), new File("mirah-library-2.10.1.jar")])
        mirahClasspath.files

        then:
        GradleException e = thrown()
        e.message == "Cannot infer Scala class path because no repository is declared in $project"
    }

    def "inference fails if 'mirahTools' configuration is empty and no Scala library Jar is found on class path"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def mirahClasspath = project.mirahRuntime.inferScalaClasspath([new File("other.jar"), new File("other2.jar")])
        mirahClasspath.files

        then:
        GradleException e = thrown()
        e.message.startsWith("Cannot infer Scala class path because no Scala library Jar was found. Does root project 'test' declare dependency to mirah-library? Searched classpath:")
    }

    def "allows to find Scala Jar on class path"() {
        when:
        def file = project.mirahRuntime.findScalaJar([new File("other.jar"), new File("mirah-jdbc-1.5.jar"), new File("mirah-compiler-1.7.jar")], "jdbc")

        then:
        file.name == "mirah-jdbc-1.5.jar"
    }

    def "returns null if Scala Jar not found"() {
        when:
        def file = project.mirahRuntime.findScalaJar([new File("other.jar"), new File("mirah-jdbc-1.5.jar"), new File("mirah-compiler-1.7.jar")], "library")

        then:
        file == null
    }

    def "allows to determine version of Scala Jar"() {
        expect:
        with(project.mirahRuntime) {
            getScalaVersion(new File("mirah-compiler-2.9.2.jar")) == "2.9.2"
            getScalaVersion(new File("mirah-jdbc-2.9.2.jar")) == "2.9.2"
            getScalaVersion(new File("mirah-library-2.10.0-SNAPSHOT.jar")) == "2.10.0-SNAPSHOT"
            getScalaVersion(new File("mirah-library-2.10.0-rc-3.jar")) == "2.10.0-rc-3"
        }
    }

    def "returns null if Scala version cannot be determined"() {
        expect:
        with(project.mirahRuntime) {
            getScalaVersion(new File("mirah-compiler.jar")) == null
            getScalaVersion(new File("groovy-compiler-2.1.0.jar")) == null
        }
    }
}
