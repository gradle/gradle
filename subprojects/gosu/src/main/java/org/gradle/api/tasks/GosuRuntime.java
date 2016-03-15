/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks;

import org.gradle.api.Buildable;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.Nullable;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides information related to the Gosu runtime used in a project. Added by the
 * {@code org.gradle.api.plugins.gosu.GosuBasePlugin} as a project extension named {@code gosuRuntime}.
 *
 * <p>Example usage:
 *
 * <pre autoTested="">
 *     apply plugin: 'gosu'
 *
 *     repositories {
 *         mavenCentral()
 *     }
 *
 *     dependencies {
 *         compile 'org.gosu-lang.gosu:gosu-core-api:1.13.1"
 *     }
 *
 *     def gosuClasspath = gosuRuntime.inferGosuClasspath(configurations.compile)
 *     // The returned class path can be used to configure the 'gosuClasspath' property of tasks
 *     // such as 'GosuCompile' or 'GosuDoc'.
 * </pre>
 */
@Incubating
public class GosuRuntime {
    private static final Pattern GOSU_JAR_PATTERN = Pattern.compile("gosu-(\\w.*?)-(\\d.*).jar");
    private static final String LF = System.lineSeparator();

    private final Project _project;

    public GosuRuntime(Project project) {
        _project = project;
    }

    /**
     * Searches the specified classpath for a 'gosu-core-api' Jar, and returns a classpath
     * containing a corresponding (same version) 'gosu-ant-tools' Jar and its dependencies.
     *
     * <p>The returned class path may be empty, or may fail to resolve when asked for its contents.
     *
     * @param classpath a classpath containing a 'gosu-core-api' Jar
     * @return a classpath containing a corresponding 'gosu-core' Jar and its dependencies
     */
    public FileCollection inferGosuClasspath(final Iterable<File> classpath) {

        /**
         *
         * @return a classpath containing a corresponding 'gosu-core' Jar and its dependencies
         */
        return new LazilyInitializedFileCollection() {
            @Override
            public String getDisplayName() {
                return "Gosu runtime classpath";
            }

            @Override
            public FileCollection createDelegate() {
                if (_project.getRepositories().isEmpty()) {
                    throw new GradleException("Cannot infer Gosu classpath because no repository is declared in " + _project);
                }

                File gosuCoreApiJar = findGosuJar(classpath, "core-api");

                if (gosuCoreApiJar == null) {
                    List<String> classpathAsStrings = new ArrayList<String>();
                    for(File file : classpath) {
                        classpathAsStrings.add(file.getAbsolutePath());
                    }
                    String flattenedClasspath = String.join(":", classpathAsStrings);
                    String errorMsg = String.format("Cannot infer Gosu classpath because the Gosu Core API Jar was not found." + LF
                        + "Does %s declare dependency to gosu-core-api? Searched classpath: %s.", _project, flattenedClasspath) + LF
                        + "An example dependencies closure may resemble the following:" + LF
                        + LF
                        + "dependencies {" + LF
                        + "    compile 'org.gosu-lang.gosu:gosu-core-api:1.10' //a newer version may be available" + LF
                        + "}" + LF;
                    throw new GradleException(errorMsg);
                }

                String gosuCoreApiRawVersion = getGosuVersion(gosuCoreApiJar);

                if (gosuCoreApiRawVersion == null) {
                    throw new AssertionError(String.format("Unexpectedly failed to parse version of Gosu Jar file: %s in %s", gosuCoreApiJar, _project));
                }

                //Use Gradle's VersionNumber construct, which implements Comparable
                VersionNumber gosuCoreApiVersion = VersionNumber.parse(gosuCoreApiRawVersion);

                //Gosu >= 1.10 is required
                if (!gosuCoreApiRawVersion.endsWith("-SNAPSHOT") && gosuCoreApiVersion.getBaseVersion().compareTo(VersionNumber.parse("1.10")) < 0) {
                    throw new GradleException(String.format("Please declare a dependency on Gosu version 1.10 or greater. Found: %s", gosuCoreApiRawVersion));
                }

                return _project.getConfigurations().detachedConfiguration(
                    new DefaultExternalModuleDependency("org.gosu-lang.gosu", "gosu-ant-tools", gosuCoreApiRawVersion));
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
     * Searches the specified class path for a Gosu Jar file (gosu-core, gosu-core-api, etc.) with the specified appendix (core-api, ant-tools, core etc.).
     * If no such file is found, {@code null} is returned.
     *
     * @param classpath the class path to search
     * @param appendix the appendix to search for
     * @return a Gosu Jar file with the specified appendix
     */
    @Nullable
    public File findGosuJar(Iterable<File> classpath, String appendix) {
        for (File file : classpath) {
            Matcher matcher = GOSU_JAR_PATTERN.matcher(file.getName());
            if (matcher.matches() && matcher.group(1).equals(appendix)) {
                return file;
            }
        }
        return null;
    }

    /**
     * Determines the version of a Gosu Jar file (gosu-core, gosu-core-api, etc.).
     * If the version cannot be determined, or the file is not a Gosu
     * Jar file, {@code null} is returned.
     *
     * <p>Implementation note: The version is determined by parsing the file name, which
     * is expected to match the pattern 'gosu-[component]-[version].jar'.
     *
     * @param gosuJar a Gosu Jar file
     * @return the version of the Gosu Jar file
     */
    @Nullable
    public String getGosuVersion(File gosuJar) {
        Matcher matcher = GOSU_JAR_PATTERN.matcher(gosuJar.getName());
        return matcher.matches() ? matcher.group(2) : null;
    }
}
