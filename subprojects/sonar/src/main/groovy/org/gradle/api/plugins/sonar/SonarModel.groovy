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

import org.gradle.util.ConfigureUtil
import org.gradle.api.plugins.sonar.internal.PropertyConverter

/**
 * Configuration options for Sonar analysis.
 */
class SonarModel {
    SonarServer server

    SonarDatabase database

    SonarProject project

    private final List<Closure> propertyProcessors = []

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

    Map<String, String> convertProperties() {
        def converter = new PropertyConverter(this)
        converter.convertProperties(GlobalProperty, propertyProcessors)
    }
}

/**
 * Configuration options for the Sonar server.
 */
class SonarServer {
    @GlobalProperty("sonar.host.url")
    String url
}

/**
 * Configuration options for the Sonar database.
 */
class SonarDatabase {
    @GlobalProperty("sonar.jdbc.url")
    String url
    @GlobalProperty("sonar.jdbc.driverClassName")
    String driverClass
    @GlobalProperty("sonar.jdbc.username")
    String username
    @GlobalProperty("sonar.jdbc.password")
    String password
}

/**
 * Configuration options for a project to be analyzed with Sonar.
 */
class SonarProject {
    boolean skip = false

    @ProjectProperty("sonar.projectKey")
    String key
    @ProjectProperty("sonar.projectName")
    String name
    @ProjectProperty("sonar.projectDescription")
    String description
    @ProjectProperty("sonar.projectVersion")
    String version

    File projectDir
    File buildDir
    List<File> mainSourceDirs
    List<File> testSourceDirs
    List<File> classesDirs
    @ProjectProperty("sonar.surefire.reportsPath")
    File testReportsDir

    Iterable<File> dependencies

    SonarJavaSettings java

    @ProjectProperty("sonar.dynamicAnalysis")
    String analysisMode

    List<SonarProject> subprojects = []

    private final List<Closure> propertyProcessors = []

    void java(Closure config) {
        ConfigureUtil.configure(config, java)
    }

    void withProjectProperties(Closure block) {
        propertyProcessors << block
    }

    Map<String, String> convertProperties() {
        def converter = new PropertyConverter(this)
        converter.convertProperties(ProjectProperty, propertyProcessors)
    }
}

/**
 * Configuration options for Java code to be analyzed with Sonar.
 */
class SonarJavaSettings {
    @ProjectProperty("sonar.java.source")
    String sourceCompatibility
    @ProjectProperty("sonar.java.target")
    String targetCompatibility
}
