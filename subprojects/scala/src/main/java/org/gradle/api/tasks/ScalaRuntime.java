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

import org.gradle.api.Buildable;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.file.collections.FailingFileCollection;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities;
import org.gradle.api.plugins.scala.ScalaPluginExtension;

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
public class ScalaRuntime {

    private final Project project;
    private final JvmEcosystemUtilities jvmEcosystemUtilities;

    public ScalaRuntime(Project project) {
        this.project = project;
        this.jvmEcosystemUtilities = ((ProjectInternal) project).getServices().get(JvmEcosystemUtilities.class);
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
        return new LazilyInitializedFileCollection() {
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
                String zincVersion = project.getExtensions().getByType(ScalaPluginExtension.class).getZincVersion().get();

                DefaultExternalModuleDependency compilerInterfaceJar = new DefaultExternalModuleDependency(
                    "org.scala-sbt",
                    "compiler-interface",
                    zincVersion
                );

                ScalaLibraryJar scalaLibraryJar = ScalaLibraryJar.find(classpath, project);

                Configuration scalaRuntimeClasspath = project.getConfigurations().detachedConfiguration(
                    scalaLibraryJar.compilerJar(),
                    scalaLibraryJar.compilerBridgeJar(zincVersion),
                    compilerInterfaceJar
                );

                jvmEcosystemUtilities.configureAsRuntimeClasspath(scalaRuntimeClasspath);
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
}
