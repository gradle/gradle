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
 * Configuration options for Sonar analysis.
 */
class SonarModel {
    @IncludeProperties
    SonarServer server

    @IncludeProperties
    SonarDatabase database

    @SonarProperty("sonar.branch")
    String branch

    @SonarProperty("sonar.profile")
    String profile

    File bootstrapDir

    String gradleVersion

    List<Closure> propertyProcessors = []

    void server(Closure config) {
        ConfigureUtil.configure(config, server)
    }

    void database(Closure config) {
        ConfigureUtil.configure(config, server)
    }

    void project(Closure config) {
        ConfigureUtil.configure(config, server)
    }

    void withGlobalProperties(Closure block) {
        propertyProcessors << block
    }
}

/**
 * Configuration options for the Sonar server.
 */
class SonarServer {
    @SonarProperty("sonar.host.url")
    String url
}

/**
 * Configuration options for the Sonar database.
 */
class SonarDatabase {
    @SonarProperty("sonar.jdbc.url")
    String url
    @SonarProperty("sonar.jdbc.driverClassName")
    String driverClassName
    @SonarProperty("sonar.jdbc.username")
    String username
    @SonarProperty("sonar.jdbc.password")
    String password
}

/**
 * Configuration options for a project to be analyzed with Sonar.
 */
class SonarProject {
    String key
    String name
    String description
    String version
    @SonarProperty("sonar.projectDate")
    String date
    boolean skip = false

    File baseDir
    File workDir
    List<File> sourceDirs = []
    List<File> testDirs = []
    List<File> binaryDirs = []
    FileCollection libraries// = new SimpleFileCollection()

    @SonarProperty("sonar.importSources")
    boolean importSources = true
    @SonarProperty("sonar.sourceEncoding")
    String sourceEncoding
    @SonarProperty("sonar.exclusions")
    String sourceExclusions

    @SonarProperty("sonar.skipDesign")
    boolean skipDesignAnalysis = false

    @SonarProperty("sonar.surefire.reportsPath")
    File testReportPath
    @SonarProperty("sonar.cobertura.reportPath")
    File coberturaReportPath
    @SonarProperty("sonar.clover.reportPath")
    File cloverReportPath

    @SonarProperty("sonar.language")
    String language

    @IncludeProperties
    SonarJavaSettings java

    @SonarProperty("sonar.dynamicAnalysis")
    String dynamicAnalysis

    List<SonarProject> subprojects = []

    List<Closure> propertyProcessors = []

    void java(Closure config) {
        ConfigureUtil.configure(config, java)
    }

    void withProjectProperties(Closure block) {
        propertyProcessors << block
    }
}

/**
 * Configuration options for Java code to be analyzed with Sonar.
 */
class SonarJavaSettings {
    @SonarProperty("sonar.java.source")
    String sourceCompatibility
    @SonarProperty("sonar.java.target")
    String targetCompatibility
}
