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
package org.gradle.api.plugins.scala

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.util.HelperUtil
import org.junit.Test

class ScalaRuntimeExtensionTest {
    private final Project project = HelperUtil.createRootProject()

    @Test void allowsToInferScalaCompilerClasspath() {
        project.plugins.apply(ScalaBasePlugin)

        project.repositories {
            mavenCentral()
        }

        def classpath = project.scalaRuntime.inferScalaCompilerClasspath([new File("other.jar"), new File("scala-library-2.10.0.jar")])

        assert classpath instanceof Configuration
        assert classpath.state == Configuration.State.UNRESOLVED
        assert classpath.dependencies.size() == 1
        classpath.dependencies.iterator().next().with {
            assert group == "org.scala-lang"
            assert name == "scala-compiler"
            assert version == "2.10.0"
        }
    }

    @Test void inferenceFallsBackToScalaToolsIfNoRepositoryDeclared() {
        project.plugins.apply(ScalaBasePlugin)

        def classpath = project.scalaRuntime.inferScalaCompilerClasspath([new File("other.jar"), new File("scala-library-2.10.0.jar")])

        assert classpath == project.configurations.scalaTools
    }

    @Test void inferenceFallsBackToScalaToolsIfScalaLibraryNotFoundOnClassPath() {
        project.plugins.apply(ScalaBasePlugin)

        def classpath = project.scalaRuntime.inferScalaCompilerClasspath([new File("other.jar"), new File("other2.jar")])

        assert classpath == project.configurations.scalaTools
    }

    @Test void allowsToFindScalaJarInClassPath() {
        project.plugins.apply(ScalaBasePlugin)

        def file = project.scalaRuntime.findScalaJar([new File("other.jar"), new File("scala-jdbc-1.5.jar"), new File("scala-compiler-1.7.jar")], "jdbc")

        assert file.name == "scala-jdbc-1.5.jar"
    }

    @Test void returnsNullIfScalaJarNotFound() {
        project.plugins.apply(ScalaBasePlugin)

        def file = project.scalaRuntime.findScalaJar([new File("other.jar"), new File("scala-jdbc-1.5.jar"), new File("scala-compiler-1.7.jar")], "library")

        assert file == null
    }

    @Test void allowsToDetermineVersionOfScalaJar() {
        project.plugins.apply(ScalaBasePlugin)

        assert project.scalaRuntime.getScalaVersion(new File("scala-compiler-2.9.2.jar")) == "2.9.2"
        assert project.scalaRuntime.getScalaVersion(new File("scala-jdbc-2.9.2.jar")) == "2.9.2"
        assert project.scalaRuntime.getScalaVersion(new File("scala-library-2.10.0-SNAPSHOT.jar")) == "2.10.0-SNAPSHOT"
        assert project.scalaRuntime.getScalaVersion(new File("scala-library-2.10.0-rc-3.jar")) == "2.10.0-rc-3"
    }

    @Test void returnsNullIfScalaVersionCannotBeDetermined() {
        project.plugins.apply(ScalaBasePlugin)

        assert project.scalaRuntime.getScalaVersion(new File("scala-compiler.jar")) == null
        assert project.scalaRuntime.getScalaVersion(new File("groovy-compiler-2.1.0.jar")) == null
    }
}
