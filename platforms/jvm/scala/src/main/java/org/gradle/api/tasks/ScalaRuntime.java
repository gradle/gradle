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
package org.gradle.api.tasks;

import com.google.common.collect.FluentIterable;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.scala.internal.ScalaJar;
import org.gradle.api.tasks.scala.internal.ScalaRuntimeHelper;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Provides information related to the Scala runtime(s) used in a project. Added by the
 * {@code org.gradle.api.plugins.scala.ScalaBasePlugin} as a project extension named {@code scalaRuntime}.
 *
 * <p>Example usage:
 *
 * <pre class='autoTested'>
 *     plugins {
 *         id 'scala'
 *     }
 *
 *     repositories {
 *         mavenCentral()
 *     }
 *
 *     dependencies {
 *         implementation "org.scala-lang:scala-library:2.13.12"
 *     }
 *
 *     def scalaClasspath = scalaRuntime.inferScalaClasspath(configurations.compileClasspath)
 *     // The returned class path can be used to configure the 'scalaClasspath' property of tasks
 *     // such as 'ScalaCompile' or 'ScalaDoc', or to execute these and other Scala tools directly.
 * </pre>
 */
public abstract class ScalaRuntime {

    private final Project project;
    private final ScalaRuntimeHelper helper;

    public ScalaRuntime(Project project) {
        this.project = project;
        this.helper = ScalaRuntimeHelper.create(project);
    }

    /**
     * Searches the specified class path for a 'scala-library' Jar, and returns a class path
     * containing a corresponding (same version) 'scala-compiler' Jar and its dependencies.
     *
     * <p>The returned class path may be empty, or may fail to resolve when asked for its contents.
     *
     * @param classpath a class path containing a 'scala-library' Jar
     * @return a class path containing a corresponding 'scala-compiler' Jar and its dependencies
     */
    public FileCollection inferScalaClasspath(Iterable<File> classpath) {
        Provider<String> scalaVersion = Providers.of(classpath).map(helper::getScalaVersion);
        return helper.configureAsScalaClasspath(project.getConfigurations().detachedConfiguration(), scalaVersion);
    }

    /**
     * Searches the specified class path for a Scala Jar file (scala-compiler, scala-library,
     * scala-jdbc, etc.) with the specified appendix (compiler, library, jdbc, etc.).
     * If no such file is found, {@code null} is returned.
     *
     * @param classpath the class path to search
     * @param appendix the appendix to search for
     * @return a Scala Jar file with the specified appendix
     */
    @Nullable
    public File findScalaJar(Iterable<File> classpath, String appendix) {
        Iterable<ScalaJar> scalaJars = ScalaJar.inspect(classpath, module -> module.equals(appendix));
        return FluentIterable.from(scalaJars).transform(ScalaJar::getFile).first().orNull();
    }

    /**
     * Determines the version of a Scala Jar file (scala-compiler, scala-library,
     * scala-jdbc, etc.). If the version cannot be determined, or the file is not a Scala
     * Jar file, {@code null} is returned.
     *
     * <p>Implementation note: The version is determined by parsing the file name, which
     * is expected to match the pattern 'scala-[component]-[version].jar'.
     *
     * @param file a Scala Jar file
     * @return the version of the Scala Jar file
     */
    @Nullable
    public String getScalaVersion(File file) {
        ScalaJar scalaJar = ScalaJar.inspect(file, module -> true);
        return scalaJar != null ? scalaJar.getVersion() : null;
    }
}
