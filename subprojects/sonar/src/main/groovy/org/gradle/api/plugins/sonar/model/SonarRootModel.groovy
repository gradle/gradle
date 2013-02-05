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
package org.gradle.api.plugins.sonar.model

import org.gradle.util.ConfigureUtil
import org.gradle.api.file.FileCollection

/**
 * Base interface for Sonar models on analysis roots and their subprojects.
 */
interface SonarModel {
    /**
     * Returns per-project configuration options.
     *
     * @return per-project configuration options
     */
    SonarProject getProject()

    /**
     * Returns the Sonar model objects for this project's child projects.
     *
     * @return the Sonar model objects for this project's child projects
     */
    List<SonarModel> getChildModels()
}

/**
 * Configuration options for a project that has the <tt>sonar</tt> plugin applied.
 */
class SonarRootModel implements SonarModel {
    /**
     * Configuration options related to the Sonar server.
     */
    @IncludeProperties
    SonarServer server

    /**
     * Configuration options related to the Sonar database.
     */
    @IncludeProperties
    SonarDatabase database

    /**
     * Per-project configuration options.
     */
    SonarProject project

    /**
     * Name of the version control branch that is analyzed. Two branches of the same
     * project are considered as different projects in Sonar. Defaults to <tt>null</tt>.
     */
    @SonarProperty("sonar.branch")
    String branch

    /**
     * Selects one of the quality profiles configured via the Sonar web interface,
     * overriding any selection made there. Defaults to <tt>null</tt>.
     */
    @SonarProperty("sonar.profile")
    String profile

    /**
     * Directory where the Sonar client library will be stored. The correct version
     * of the client library is automatically downloaded from the Sonar server
     * before analysis begins. Defaults to <tt>$project.buildDir/sonar</tt>.
     */
    File bootstrapDir

    /**
     * The Gradle version to be reported to the Sonar client library. Only used for
     * identification purposes. Defaults to the current Gradle version.
     */
    String gradleVersion

    /**
     * Post-processors for global Sonar properties.
     *
     * @see #withGlobalProperties
     */
    List<Closure> propertyProcessors = []

    /**
     * Configuration options for child projects of this project.
     */
    List<SonarModel> childModels = []

    /**
     * Configures server configuration options. The specified closure
     * delegates to an instance of {@link SonarServer}.
     *
     * @param server configuration options
     */
    void server(Closure config) {
        ConfigureUtil.configure(config, server)
    }

    /**
     * Configures database configuration options. The specified closure
     * delegates to an instance of {@link SonarDatabase}.
     *
     * @param database configuration options
     */
    void database(Closure config) {
        ConfigureUtil.configure(config, database)
    }

    /**
     * Configures per-project configuration options. The specified closure
     * delegates to an instance of {@link SonarProject}.
     *
     * @param per-project configuration options
     */
    void project(Closure config) {
        ConfigureUtil.configure(config, project)
    }

    /**
     * Registers a closure for post-processing the global Sonar properties covered by
     * <tt>SonarRootModel<tt>, and for adding further properties not covered by
     * that model. The properties are passed to the closure as a map
     * of String keys and values. Keys correspond to the Sonar properties as described in the
     * <a href="http://docs.codehaus.org/display/SONAR/Advanced+parameters">Sonar documentation</a>.
     *
     * <p>Evaluation of the closure is deferred until build execution time. If this
     * method is called multiple times, closures will be evaluated in the order
     * they have been registered.
     *
     * <p>Example:
     *
     * <pre>
     * withGlobalProperties { props ->
     *     // add further properties
     *     props["some.sonar.property"] = "some value"
     *     props["another.sonar.property"] = "another value"
     *
     *     // modify existing properties (rarely necessary)
     *     props["sonar.branch"] = "some-branch"
     *     props.remove("sonar.profile")
     * }
     * </pre>
     *
     * @param block a closure for post-processing global Sonar properties
     */
    void withGlobalProperties(Closure block) {
        propertyProcessors << block
    }
}

/**
 * Configuration options for subprojects of a project that has the <tt>sonar</tt> plugin applied.
 */
class SonarProjectModel implements SonarModel {
    /**
     * Per-project configuration options.
     */
    SonarProject project

    /**
     * Configuration options for child projects of this project.
     */
    List<SonarModel> childModels = []

    /**
     * Configures per-project configuration options. The specified closure
     * delegates to an instance of {@link SonarProject}.
     *
     * @param config per-project configuration options
     */
    void project(Closure config) {
        ConfigureUtil.configure(config, project)
    }
}

/**
 * Configuration options for the Sonar web server. Defaults match the defaults
 * for a locally running server.
 */
class SonarServer {
    /**
     * The URL for the Sonar web server. Defaults to <tt>http://localhost:9000</tt>.
     */
    @SonarProperty("sonar.host.url")
    String url
}

/**
 * Configuration options for the Sonar database. Defaults match the defaults for
 * a locally running server (with embedded database).
 */
class SonarDatabase {
    /**
     * The JDBC URL for the Sonar database. Defaults to <tt>jdbc:derby://localhost:1527/sonar</tt>.
     */
    @SonarProperty("sonar.jdbc.url")
    String url

    /**
     * The name of the JDBC driver class. Defaults to <tt>org.apache.derby.jdbc.ClientDriver</tt>.
     */
    @SonarProperty("sonar.jdbc.driverClassName")
    String driverClassName
    /**
     * The JDBC username for the Sonar database. Defaults to <tt>sonar</tt>.
     */
    @SonarProperty("sonar.jdbc.username")
    String username
    /**
     * The JDBC password for the Sonar database. Defaults to <tt>sonar</tt>.
     */
    @SonarProperty("sonar.jdbc.password")
    String password
}

/**
 * Per-project configuration options.
 */
class SonarProject {
    /**
     * The identifier for the project. Defaults to <tt>$project.group:$project.name</tt>.
     */
    String key

    /**
     * The name for the project. Defaults to <tt>$project.name</tt>.
     */
    String name

    /**
     * A description for the project. Defaults to <tt>$project.description</tt>.
     */
    String description

    /**
     * The version of the project. Defaults to <tt>$project.version</tt>.
     */
    String version

    /**
     * The date when analysis was performed. Format is <tt>yyyy-MM-dd</tt>
     * (e.g. <tt>2010-12-25</tt>). Defaults to the current date.
     */
    @SonarProperty("sonar.projectDate")
    String date

    /**
     * Tells whether to skip analysis of this project. Allows to only analyze a
     * subset of projects in a Gradle build. Defaults to <tt>false</tt>.
     */
    boolean skip = false

    /**
     * The base directory for the analysis. Defaults to <tt>$project.projectDir</tt>.
     */
    File baseDir

    /**
     * The working directory for the analysis. Defaults to <tt>$project.buildDir/sonar<tt>.
     */
    File workDir

    /**
     * The directories containing the project's production source code to be analyzed.
     * Defaults to <tt>project.sourceSets.main.allSource.srcDirs</tt>.
     */
    List<File> sourceDirs = []

    /**
     * The directories containing the project's test source code to be analyzed.
     * Defaults to <tt>project.sourceSets.test.allSource.srcDirs</tt>.
     */
    List<File> testDirs = []

    /**
     * The directories containing the project's compiled code to be analyzed.
     * Defaults to <tt>main.output.classesDir</tt>.
     */
    List<File> binaryDirs = []

    /**
     * A class path containing the libraries used by this project.
     * Defaults to <tt>project.sourceSets.main.compileClasspath + Jvm.current().runtimeJar</tt>.
     */
    FileCollection libraries// = new SimpleFileCollection()

    /**
     * Tells whether to the project's source code should be stored and made available
     * via the Sonar web interface. Defaults to <tt>true</tt>.
     */
    @SonarProperty("sonar.importSources")
    boolean importSources = true

    /**
     * The character encoding for the project's source files. Accepts the same
     * values as {@link java.nio.charset.Charset} (e.g. <tt>UTF-8<tt>, <tt>MacRoman</tt>,
     * <tt>Shift_JIS</tt>). Defaults to the JVM's platform encoding.
     */
    @SonarProperty("sonar.sourceEncoding")
    String sourceEncoding

    /**
     * Source files to be excluded from analysis. Specified as comma-separated
     * list of Ant-style patterns which are relative to a source directory.
     *
     * <p>Example: <tt>com/mycompany/*&#47;.java, **&#47;*Dummy.java</tt>.
     *
     * <p>Defaults to <tt>null</tt>.
     */
    @SonarProperty("sonar.exclusions")
    String sourceExclusions

    /**
     * Tells whether to skip analysis of the project's design. Defaults to <tt>false</tt>.
     */
    @SonarProperty("sonar.skipDesign")
    boolean skipDesignAnalysis = false

    /**
     * The directory containing the JUnit XML test report. Defaults to
     * <tt>project.test.testResultsDir</tt>.
     */
    @SonarProperty("sonar.surefire.reportsPath")
    File testReportPath

    /**
     * The Cobertura XML report file. Defaults to <tt>null</tt>.
     */
    @SonarProperty("sonar.cobertura.reportPath")
    File coberturaReportPath

    /**
     * The Clover XML report file. Defaults to <tt>null</tt>.
     */
    @SonarProperty("sonar.clover.reportPath")
    File cloverReportPath

    /**
     * The programming language to be analyzed. Only one language per project
     * can be analyzed. Defaults to <tt>java</tt>.
     */
    @SonarProperty("sonar.language")
    String language

    /**
     * Java-related configuration options.
     */
    @IncludeProperties
    SonarJavaSettings java

    /**
     * Whether and how to perform dynamic analysis. Dynamic analysis includes
     * the analysis of test and code coverage reports. The following values are allowed:
     *
     * <ul>
     *     <li>
     *         <tt>reuseReports</tt>: Test and/or coverage reports will be produced by the
     *         Gradle build and passed via <tt>testReportPath</tt>, <tt>coberturaReportPath</tt>,
     *         and <tt>cloverReportPath</tt>.
     *     </li>
     *     <li>
     *         <tt>false</tt>: No dynamic analysis will be performed.
     *     </li>
     *     <li>
     *         <tt>true</tt>: Test and/or coverage reports will be produced by Sonar. This mode
     *         is not supported by the Gradle Sonar plugin.
     *     </li>
     * </ul>
     *
     * Defaults to <tt>reuseReports</tt>.
     */
    @SonarProperty("sonar.dynamicAnalysis")
    String dynamicAnalysis

    /**
     * Post-processors for per-project Sonar properties.
     *
     * @see #withProjectProperties
     */
    List<Closure> propertyProcessors = []

    /**
     * Configures Java-related configuration options. The specified closure delegates
     * to an instance of type {@link SonarJavaSettings}.
     *
     * @param config Java-related configuration options
     */
    void java(Closure config) {
        ConfigureUtil.configure(config, java)
    }

    /**
     * Registers a closure for post-processing the per-project Sonar properties covered by
     * <tt>SonarProjectModel<tt>, and for adding further properties not covered by
     * that model. The properties are passed to the closure as a map
     * of String keys and values. Keys correspond to the Sonar properties as described in the
     * <a href="http://docs.codehaus.org/display/SONAR/Advanced+parameters">Sonar documentation</a>.
     *
     * <p>Evaluation of the closure is deferred until build execution time. If this
     * method is called multiple times, closures will be evaluated in the order
     * they have been registered.
     *
     * <p>Example:
     *
     * <pre>
     * withProjectProperties { props ->
     *     // add further properties
     *     props["some.sonar.property"] = "some value"
     *     props["another.sonar.property"] = "another value"
     *
     *     // modify existing properties (rarely necessary)
     *     props["sonar.sourceEncoding"] = "UTF-8"
     *     props.remove("sonar.projectDate")
     * }
     * </pre>
     *
     * @param block a closure for post-processing per-project Sonar properties
     */
    void withProjectProperties(Closure block) {
        propertyProcessors << block
    }
}

/**
 * Java-related configuration options for the project to be analyzed.
 */
class SonarJavaSettings {
    /**
     * The source compatibility of the Java code. Defaults to
     * <tt>project.sourceCompatibility</tt>.
     */
    @SonarProperty("sonar.java.source")
    String sourceCompatibility

    /**
     * The target compatibility of the Java code. Defaults to
     * <tt>project.targetCompatibility</tt>.
     */
    @SonarProperty("sonar.java.target")
    String targetCompatibility
}
