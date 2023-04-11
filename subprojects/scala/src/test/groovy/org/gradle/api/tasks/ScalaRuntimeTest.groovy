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

    def "inferred Scala class path contains 'scala-compiler' repository dependency and 'compiler-bridge' matching 'scala-library' Jar found on class path"() {
        project.repositories {
            mavenCentral()
        }
        when:
        def classpath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("scala-library-2.10.1.jar")])
        then:
        assertHasCorrectDependencies(classpath, ScalaBasePlugin.DEFAULT_ZINC_VERSION)
    }

    def "inferred Scala class path contains 'scala-compiler' repository dependency and 'compiler-bridge' matching 'scala-library' Jar found on class path with specified zinc version"() {
        project.repositories {
            mavenCentral()
        }
        def useZincVersion = "1.3.4"
        project.scala {
            zincVersion = useZincVersion
        }
        when:
        def classpath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("scala-library-2.10.1.jar")])
        then:
        assertHasCorrectDependencies(classpath, useZincVersion)
    }

    private void assertHasCorrectDependencies(classpath, zincVersion) {
        assert classpath instanceof LazilyInitializedFileCollection
        assert classpath.sourceCollections.size() == 1
        with(classpath.sourceCollections[0]) {
            assert it instanceof Configuration
            assert it.state == Configuration.State.UNRESOLVED
            assert it.dependencies.size() == 3
            assert it.dependencies.any { d ->
                d.group == "org.scala-lang" &&
                    d.name == "scala-compiler" &&
                    d.version == "2.10.1"
            }
            assert it.dependencies.any { d ->
                d.group == "org.scala-sbt" &&
                    d.name == "compiler-bridge_2.10" &&
                    d.version == zincVersion
            }
            assert it.dependencies.any { d ->
                d.group == "org.scala-sbt" &&
                    d.name == "compiler-interface" &&
                    d.version == zincVersion
            }
        }
    }

    def "inferred Scala class path contains 'scala3-compiler_3' repository dependency and 'compiler-bridge' matching 'scala-library' Jar found on class path"() {
        project.repositories {
            mavenCentral()
        }
        when:
        def classpath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("scala3-library_3-3.0.1.jar")])
        then:
        assertHasCorrectScala3Dependencies(classpath)
    }


    private void assertHasCorrectScala3Dependencies(classpath) {
        assert classpath instanceof LazilyInitializedFileCollection
        assert classpath.sourceCollections.size() == 1
        with(classpath.sourceCollections[0]) {
            assert it instanceof Configuration
            assert it.state == Configuration.State.UNRESOLVED
            assert it.dependencies.size() == 4
            assert it.dependencies.any { d ->
                d.group == "org.scala-lang" &&
                    d.name == "scala3-compiler_3" &&
                    d.version == "3.0.1"
            }
            assert it.dependencies.any { d ->
                d.group == "org.scala-lang" &&
                    d.name == "scala3-sbt-bridge" &&
                    d.version == "3.0.1"
            }
            assert it.dependencies.any { d ->
                d.group == "org.scala-lang" &&
                    d.name == "scala3-interfaces" &&
                    d.version == "3.0.1"
            }
            assert it.dependencies.any { d ->
                d.group == "org.scala-lang" &&
                    d.name == "scaladoc_3" &&
                    d.version == "3.0.1"
            }
        }
    }

    def "inference fails if 'scalaTools' configuration is empty and no repository declared"() {
        when:
        def scalaClasspath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("scala-library-2.10.1.jar")])
        scalaClasspath.files

        then:
        GradleException e = thrown()
        e.message == "Could not resolve all files for configuration ':detachedConfiguration1'."
        e.cause.message.startsWith("Cannot resolve external dependency org.scala-lang:scala-compiler:2.10.1 because no repositories are defined.")
    }

    def "inference fails if 'scalaTools' configuration is empty and no Scala library Jar is found on class path"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def scalaClasspath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("other2.jar")])
        scalaClasspath.files

        then:
        GradleException e = thrown()
        e.message.startsWith("Cannot infer Scala class path because no Scala library Jar was found. Does root project 'test-project' declare dependency to scala-library? Searched classpath:")
    }
}
