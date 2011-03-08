/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.sonar

import org.sonar.batch.bootstrapper.Bootstrapper
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.util.ClasspathUtil

/**
 * Analyzes a project and stores the results in the Sonar database.
 */
class Sonar extends ConventionTask {
    /**
     * The Sonar server to connect to.
     */
    @Input
    String serverUrl

    /**
     * The directory to be used for caching files downloaded from the Sonar server.
     */
    @Input
    File bootstrapDir = new File(System.getProperty("java.io.tmpdir"), "sonar-bootstrap")

    /**
     * The base directory for the project to be analyzed.
     */
    @Input
    File projectDir

    /**
     * The directories containing the production sources of the project to be analyzed.
     */
    @InputDirectory
    Set<File> projectMainSourceDirs = []

    /**
     * The directories containing the test sources of the project to be analyzed.
     */
    @InputDirectory
    Set<File> projectTestSourceDirs = []

    /**
     * The directories containing the class files of the project to be analyzed.
     */
    @InputDirectory
    Set<File> projectClassesDirs = []

    /**
     * The dependencies of the project to be analyzed. Typically these will be Jar files.
     */
    @InputDirectory
    Set<File> projectDependencies = []

    /**
     * A unique key for identifying the project to be analyzed.
     */
    @Input
    @Optional
    String projectKey

    /**
     * The name of the project to be analyzed.
     */
    @Input
    @Optional
    String projectName

    /**
     * The description of the project to be analyzed.
     */
    @Input
    @Optional
    String projectDescription

    /**
     * The version of the project to be analyzed.
     */
    @Input
    @Optional
    String projectVersion

    /**
     * Global properties for use by the Sonar code analyzer.
     */
    @Input
    Map globalProperties = [:]

    /**
     * Project-specific properties for use by the Sonar code analyzer.
     */
    @Input
    Map projectProperties = [:]

    @TaskAction
    void execute() {
        bootstrapDir.mkdirs()
        def bootstrapper = new Bootstrapper("Gradle", getServerUrl(), getBootstrapDir())

        def classLoader = bootstrapper.createClassLoader([findSonarJar()] as URL[],
                Sonar.classLoader, "groovy", "org.codehaus.groovy")

        def launcherClass = classLoader.loadClass("org.gradle.api.plugins.sonar.internal.SonarCodeAnalyzer")
        def launcher = launcherClass.newInstance()
        launcher.sonarTask = this
        launcher.execute()
    }

    /**
     * Adds the specified directory to the set of project main source directories.
     *
     * @param sourceDirs the main source directory to be added
     */
    void projectMainSourceDir(File sourceDir) {
        projectMainSourceDirs << sourceDir
    }

    /**
     * Adds the specified directories to the set of project main source directories.
     *
     * @param sourceDirs the main source directories to be added
     */
    void projectMainSourceDirs(File... sourceDirs) {
        projectMainSourceDirs.addAll(sourceDirs as List)
    }

    /**
     * Adds the specified directory to the set of project test source directories.
     *
     * @param sourceDirs the testsource directory to be added
     */
    void projectTestSourceDir(File sourceDir) {
        projectTestSourceDirs << sourceDir
    }

    /**
     * Adds the specified directories to the set of project test source directories.
     *
     * @param sourceDirs the test source directories to be added
     */
    void projectTestSourceDirs(File... sourceDirs) {
        projectTestSourceDirs.addAll(sourceDirs as List)
    }

    /**
     * Adds the specified directory to the set of project classes directories.
     *
     * @param classesDir the classes directory to be added
     */
    void projectClassesDir(File classesDir) {
        projectClassesDirs << classesDir
    }

    /**
     * Adds the specified directories to the set of project classes directories.
     *
     * @param classesDirs the classes directories to be added
     */
    void projectClassesDirs(File... classesDirs) {
        projectClassesDirs.addAll(classesDirs as List)
    }

    /**
     * Adds the specified dependency to the set of project dependencies. Typically this will be a Jar file.
     *
     * @param dependency the depedency to be added
     */
    void projectDependency(File dependency) {
        projectDependencies << dependency
    }

    /**
     * Adds the specified dependencies to the set of project dependencies. Typically these will be Jar files.
     *
     * @param dependencies the dependencies to be added
     */
    void projectDependencies(File... dependencies) {
        projectDependencies.addAll(dependencies as List)
    }

    /**
     * Adds the specified property to the map of global properties.
     * If a property with the specified name already exists, it will
     * be overwritten.
     *
     * @param name the name of the global property
     * @param value the value of the global property
     */
    void globalProperty(String name, String value) {
        globalProperties.put(name, value)
    }

    /**
     * Adds the specified properties to the map of global properties.
     * Existing properties with the same name will be overwritten.
     *
     * @param properties the global properties to be added
     */
    void globalProperties(Map properties) {
        globalProperties.putAll(properties)
    }

    /**
     * Adds the specified property to the map of project properties.
     * If a property with the specified name already exists, it will
     * be overwritten.
     *
     * @param name the name of the project property
     * @param value the value of the project property
     */
    void projectProperty(String name, String value) {
        globalProperties.put(name, value)
    }

    /**
     * Adds the specified properties to the map of project properties.
     * Existing properties with the same name will be overwritten.
     *
     * @param properties the project properties to be added
     */
    void projectProperties(Map properties) {
        projectProperties.putAll(properties)
    }

    private URL findSonarJar() {
        def url = ClasspathUtil.getClasspath(Sonar.classLoader).find { it.path.contains("gradle-sonar") }
        assert url != null, "failed to detect gradle-sonar Jar"
        url
    }
}