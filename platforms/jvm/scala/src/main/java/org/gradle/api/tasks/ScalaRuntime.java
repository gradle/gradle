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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.gradle.api.Buildable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.file.collections.FailingFileCollection;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.plugins.scala.ScalaPluginExtension;
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
 *         implementation "org.scala-lang:scala-library:2.10.1"
 *     }
 *
 *     def scalaClasspath = scalaRuntime.inferScalaClasspath(configurations.compileClasspath)
 *     // The returned class path can be used to configure the 'scalaClasspath' property of tasks
 *     // such as 'ScalaCompile' or 'ScalaDoc', or to execute these and other Scala tools directly.
 * </pre>
 */
public abstract class ScalaRuntime {

    // TODO: Deprecate this class in 9.x when we de-incubate ScalaPluginExtension#getScalaVersion()

    private final Project project;
    private final JvmPluginServices jvmPluginServices;

    public ScalaRuntime(Project project) {
        this.project = project;
        this.jvmPluginServices = ((ProjectInternal) project).getServices().get(JvmPluginServices.class);
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
    public FileCollection inferScalaClasspath(final Iterable<File> classpath) {
        // alternatively, we could return project.getLayout().files(Runnable)
        // would differ in the following ways: 1. live (not sure if we want live here) 2. no autowiring (probably want autowiring here)
        return new LazilyInitializedFileCollection(((ProjectInternal) project).getTaskDependencyFactory()) {
            @Override
            public String getDisplayName() {
                return "Scala runtime classpath";
            }

            @Override
            public FileCollection createDelegate() {
                try {
                    return inferScalaClasspath();
                } catch (RuntimeException e) {
                    return new FailingFileCollection(getDisplayName(), e);
                }
            }

            private Configuration inferScalaClasspath() {
                File scalaLibraryJar = findScalaJar(classpath, "library");
                File scala3LibraryJar = findScalaJar(classpath, "library_3");
                boolean isScala3 = scala3LibraryJar != null;
                if (scalaLibraryJar == null && scala3LibraryJar == null) {
                    throw new GradleException(String.format("Cannot infer Scala class path because no Scala library Jar was found. "
                        + "Does %s declare dependency to scala-library? Searched classpath: %s.", project, classpath));
                }

                String scalaVersion;
                if (isScala3) {
                    scalaVersion = getScalaVersion(scala3LibraryJar);
                } else {
                    scalaVersion = getScalaVersion(scalaLibraryJar);
                }

                if (scalaVersion == null) {
                    throw new AssertionError(String.format("Unexpectedly failed to parse version of Scala Jar file: %s in %s", scalaLibraryJar, project));
                }

                String zincVersion = project.getExtensions().getByType(ScalaPluginExtension.class).getZincVersion().get();

                DefaultExternalModuleDependency compilerBridgeJar = getScalaBridgeDependency(scalaVersion, zincVersion);
                compilerBridgeJar.setTransitive(false);
                compilerBridgeJar.artifact(artifact -> {
                    if (!isScala3) {
                        artifact.setClassifier("sources");
                    }
                    artifact.setType("jar");
                    artifact.setExtension("jar");
                    artifact.setName(compilerBridgeJar.getName());
                });
                DefaultExternalModuleDependency compilerInterfaceJar = getScalaCompilerInterfaceDependency(scalaVersion, zincVersion);

                Configuration scalaRuntimeClasspath = isScala3 ?
                  project.getConfigurations().detachedConfiguration(getScalaCompilerDependency(scalaVersion), compilerBridgeJar, compilerInterfaceJar, getScaladocDependency(scalaVersion)) :
                  project.getConfigurations().detachedConfiguration(getScalaCompilerDependency(scalaVersion), compilerBridgeJar, compilerInterfaceJar);
                jvmPluginServices.configureAsRuntimeClasspath(scalaRuntimeClasspath);
                return scalaRuntimeClasspath;
            }

            // let's override this so that delegate isn't created at autowiring time (which would mean on every build)
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                if (classpath instanceof Buildable) {
                    context.add(classpath);
                }
            }
        };
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
        return ScalaRuntimeHelper.findScalaJar(classpath, appendix);
    }

    /**
     * Determines the version of a Scala Jar file (scala-compiler, scala-library,
     * scala-jdbc, etc.). If the version cannot be determined, or the file is not a Scala
     * Jar file, {@code null} is returned.
     *
     * <p>Implementation note: The version is determined by parsing the file name, which
     * is expected to match the pattern 'scala-[component]-[version].jar'.
     *
     * @param scalaJar a Scala Jar file
     * @return the version of the Scala Jar file
     */
    @Nullable
    public String getScalaVersion(File scalaJar) {
        return ScalaRuntimeHelper.getScalaVersion(scalaJar);
    }

    /**
     * Determines Scala bridge jar to download. In Scala 3 it is released for each Scala
     * version together with the compiler jars. For Scala 2 we download sources jar and compile
     * it later on.
     *
     * @param scalaVersion version of scala to download the bridge for
     * @param zincVersion version of zinc relevant for Scala 2
     * @return bridge dependency to download
     */
    private DefaultExternalModuleDependency getScalaBridgeDependency(String scalaVersion, String zincVersion) {
        if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
            return new DefaultExternalModuleDependency("org.scala-lang", "scala3-sbt-bridge", scalaVersion);
        } else {
            String scalaMajorMinorVersion = Joiner.on('.').join(Splitter.on('.').splitToList(scalaVersion).subList(0, 2));
            return new DefaultExternalModuleDependency("org.scala-sbt", "compiler-bridge_" + scalaMajorMinorVersion, zincVersion);
        }
    }

    /**
     * Determines Scala compiler jar to download.
     *
     * @param scalaVersion version of scala to download the compiler for
     * @return compiler dependency to download
     */
    private DefaultExternalModuleDependency getScalaCompilerDependency(String scalaVersion) {
        if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
            return new DefaultExternalModuleDependency("org.scala-lang", "scala3-compiler_3", scalaVersion);
        } else {
            return new DefaultExternalModuleDependency("org.scala-lang", "scala-compiler", scalaVersion);
        }
    }

    /**
     * Determines Scala compiler interfaces jar to download.
     *
     * @param scalaVersion version of scala to download the compiler interfaces for
     * @param zincVersion version of zinc to download the compiler interfaces for as fallback for Scala 2
     * @return compiler interfaces dependency to download
     */
    private DefaultExternalModuleDependency getScalaCompilerInterfaceDependency(String scalaVersion, String zincVersion) {
        if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
            return new DefaultExternalModuleDependency("org.scala-lang", "scala3-interfaces", scalaVersion);
        } else {
            return new DefaultExternalModuleDependency("org.scala-sbt", "compiler-interface", zincVersion);
        }
    }

    /**
     * Determines Scaladoc jar to download. Note that scaladoc for Scala 2 is packaged along the compiler
     *
     * @param scalaVersion version of scala to download the scaladoc for
     * @return scaladoc dependency to download
     */
    private DefaultExternalModuleDependency getScaladocDependency(String scalaVersion) {
        if (scalaVersion.startsWith("3.")) {
            return new DefaultExternalModuleDependency("org.scala-lang", "scaladoc_3", scalaVersion);
        } else {
            return null;
        }
    }
}
